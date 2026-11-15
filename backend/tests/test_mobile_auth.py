from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

import jwt
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy import delete, select, update

from app.config import get_settings
from app.database import get_db
from app.main import app
from app.models import MobileRefreshToken, MobileSession, User
from app.services.local_auth import provision
from app.services.mobile_auth import refresh_hash

PASSWORD = "a-mobile-test-password"
BASE = "/api/v1/auth/mobile"


@pytest_asyncio.fixture
async def mobile_account(db_session, monkeypatch):
    monkeypatch.setattr(get_settings(), "local_auth_enabled", True)
    email = f"mobile-{uuid4()}@example.com"
    user_id = await provision(db_session, email, PASSWORD, "手机测试")
    await db_session.commit()
    yield {"email": email, "password": PASSWORD, "user_id": user_id}
    await db_session.rollback()
    await db_session.execute(delete(User).where(User.id == user_id))
    await db_session.commit()


async def sign_in(client, db_session, account):
    response = await client.post(
        BASE + "/login", json={k: account[k] for k in ("email", "password")}
    )
    assert response.status_code == 200, response.text
    assert response.headers["cache-control"] == "no-store"
    await db_session.commit()
    return response.json()


def bearer(tokens):
    return {"Authorization": "Bearer " + tokens["access_token"]}


@pytest.mark.asyncio
async def test_mobile_login_lifetimes_storage_and_web_compatibility(
    client, db_session, mobile_account
):
    tokens = await sign_in(client, db_session, mobile_account)
    claims = jwt.decode(tokens["access_token"], get_settings().secret_key, algorithms=["HS256"])
    assert 3590 <= tokens["expires_in"] <= 3600
    assert claims["exp"] - claims["iat"] <= 3600
    expires = datetime.fromisoformat(tokens["session_expires_at"])
    assert timedelta(days=29, hours=23) < expires - datetime.now(UTC) <= timedelta(days=30)
    assert tokens["user"]["email"] == mobile_account["email"]
    stored = await db_session.get(MobileRefreshToken, refresh_hash(tokens["refresh_token"]))
    assert stored is not None and stored.token_hash != tokens["refresh_token"]
    assert (await client.get("/api/v1/users/me", headers=bearer(tokens))).status_code == 200
    web = await client.post(
        "/api/v1/auth/login", json={k: mobile_account[k] for k in ("email", "password")}
    )
    assert web.status_code == 200 and web.json()["expires_in"] == 604800
    web_claims = jwt.decode(
        web.json()["access_token"], get_settings().secret_key, algorithms=["HS256"]
    )
    assert "sid" not in web_claims
    assert web_claims["exp"] - web_claims["iat"] == 604800


@pytest.mark.asyncio
async def test_rotation_does_not_extend_session_and_replay_revokes_committed_family(
    client, db_session, session_maker, mobile_account
):
    original = await sign_in(client, db_session, mobile_account)
    response = await client.post(
        BASE + "/refresh", json={"refresh_token": original["refresh_token"]}
    )
    assert response.status_code == 200
    renewed = response.json()
    assert renewed["refresh_token"] != original["refresh_token"]
    assert renewed["session_expires_at"] == original["session_expires_at"]
    await db_session.commit()
    replay = await client.post(BASE + "/refresh", json={"refresh_token": original["refresh_token"]})
    assert replay.status_code == 401
    assert replay.json()["detail"]["error_code"] == "MOBILE_REFRESH_REPLAY"
    await db_session.rollback()  # Endpoint's revocation must survive request rollback.
    async with session_maker() as check:
        session = await check.scalar(
            select(MobileSession).where(MobileSession.user_id == mobile_account["user_id"])
        )
        assert session.revoked_at is not None
    assert (await client.get("/api/v1/users/me", headers=bearer(renewed))).status_code == 401
    assert (
        await client.post(BASE + "/refresh", json={"refresh_token": renewed["refresh_token"]})
    ).status_code == 401


@pytest.mark.asyncio
async def test_logout_only_revokes_its_session_and_is_idempotent(
    client, db_session, mobile_account
):
    first = await sign_in(client, db_session, mobile_account)
    second = await sign_in(client, db_session, mobile_account)
    for _ in range(2):
        result = await client.post(BASE + "/logout", json={"refresh_token": first["refresh_token"]})
        assert result.status_code == 204
        await db_session.commit()
    assert (await client.get("/api/v1/users/me", headers=bearer(first))).status_code == 401
    assert (await client.get("/api/v1/users/me", headers=bearer(second))).status_code == 200


@pytest.mark.asyncio
async def test_unknown_secret_cannot_revoke_session(client, db_session, mobile_account):
    tokens = await sign_in(client, db_session, mobile_account)
    forged = "x" * 64
    assert (await client.post(BASE + "/refresh", json={"refresh_token": forged})).status_code == 401
    assert (await client.post(BASE + "/logout", json={"refresh_token": forged})).status_code == 204
    assert (await client.get("/api/v1/users/me", headers=bearer(tokens))).status_code == 200


@pytest.mark.asyncio
async def test_absolute_session_expiration_also_refuses_unexpired_access(
    client, db_session, mobile_account
):
    tokens = await sign_in(client, db_session, mobile_account)
    await db_session.execute(
        update(MobileSession)
        .where(MobileSession.user_id == mobile_account["user_id"])
        .values(expires_at=datetime.now(UTC) - timedelta(seconds=1))
    )
    await db_session.commit()
    assert (await client.get("/api/v1/users/me", headers=bearer(tokens))).status_code == 401
    assert (
        await client.post(BASE + "/refresh", json={"refresh_token": tokens["refresh_token"]})
    ).status_code == 401


@pytest.mark.asyncio
async def test_password_change_revokes_all_mobile_sessions(client, db_session, mobile_account):
    first = await sign_in(client, db_session, mobile_account)
    second = await sign_in(client, db_session, mobile_account)
    response = await client.post(
        "/api/v1/auth/password",
        headers=bearer(first),
        json={"current_password": PASSWORD, "new_password": "another-mobile-password"},
    )
    assert response.status_code == 204
    await db_session.commit()
    for tokens in (first, second):
        assert (await client.get("/api/v1/users/me", headers=bearer(tokens))).status_code == 401
        assert (
            await client.post(BASE + "/refresh", json={"refresh_token": tokens["refresh_token"]})
        ).status_code == 401


@pytest.mark.asyncio
async def test_disabled_account_and_disabled_local_auth(
    client, db_session, mobile_account, monkeypatch
):
    tokens = await sign_in(client, db_session, mobile_account)
    await db_session.execute(
        update(User).where(User.id == mobile_account["user_id"]).values(is_active=False)
    )
    await db_session.commit()
    assert (
        await client.post(BASE + "/refresh", json={"refresh_token": tokens["refresh_token"]})
    ).status_code == 401
    monkeypatch.setattr(get_settings(), "local_auth_enabled", False)
    for endpoint, data in (
        ("login", {"email": mobile_account["email"], "password": PASSWORD}),
        ("refresh", {"refresh_token": tokens["refresh_token"]}),
        ("logout", {"refresh_token": tokens["refresh_token"]}),
    ):
        assert (await client.post(BASE + "/" + endpoint, json=data)).status_code == 404


@pytest.mark.asyncio
async def test_wrong_password_does_not_issue_session(client, db_session, mobile_account):
    result = await client.post(
        BASE + "/login", json={"email": mobile_account["email"], "password": "wrong"}
    )
    assert result.status_code == 401
    assert (
        await db_session.scalar(
            select(MobileSession.id).where(MobileSession.user_id == mobile_account["user_id"])
        )
        is None
    )


@pytest.mark.asyncio
async def test_concurrent_refresh_serializes_and_replay_revocation_persists(
    client, db_session, session_maker, mobile_account
):
    import asyncio

    tokens = await sign_in(client, db_session, mobile_account)

    async def committed_db():
        async with session_maker() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    saved = app.dependency_overrides[get_db]
    app.dependency_overrides[get_db] = committed_db
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as concurrent:
            responses = await asyncio.gather(
                *[
                    concurrent.post(
                        BASE + "/refresh", json={"refresh_token": tokens["refresh_token"]}
                    )
                    for _ in range(2)
                ]
            )
        assert sorted(r.status_code for r in responses) == [200, 401]
        async with session_maker() as check:
            claims = jwt.decode(
                tokens["access_token"], get_settings().secret_key, algorithms=["HS256"]
            )
            session = await check.get(MobileSession, UUID(claims["sid"]))
            assert session.revoked_at is not None
    finally:
        app.dependency_overrides[get_db] = saved
