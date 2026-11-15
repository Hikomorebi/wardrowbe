from io import BytesIO
from uuid import uuid4

import pytest
from PIL import Image
from sqlalchemy import delete

from app.config import Settings, get_settings
from app.models import ClothingItem, User
from app.models.item import ItemStatus
from app.services.local_auth import provision


def photo():
    stream = BytesIO()
    Image.new("RGB", (32, 32), (92, 103, 42)).save(stream, "JPEG")
    return stream.getvalue()


@pytest.mark.asyncio
async def test_item_fields_filters_and_clear(client, auth_headers, db_session):
    location = await client.post(
        "/api/v1/locations", json={"name": "  衣柜  "}, headers=auth_headers
    )
    assert location.status_code == 201
    assert location.json()["name"] == "衣柜"
    location_id = location.json()["id"]
    created = await client.post(
        "/api/v1/items",
        headers=auth_headers,
        files={"image": ("shirt.jpg", photo(), "image/jpeg")},
        data={
            "type": "t-shirt",
            "condition": "excellent",
            "storage_location_id": location_id,
            "season": ["spring", "summer"],
            "skip_ai": "true",
        },
    )
    assert created.status_code == 201, created.text
    item = created.json()
    assert item["status"] == "ready"
    assert item["condition"] == "excellent"
    assert item["season"] == item["tags"]["season"] == ["spring", "summer"]
    match = await client.get(
        "/api/v1/items",
        headers=auth_headers,
        params={"condition": "excellent", "storage_location_id": location_id, "season": "summer"},
    )
    assert match.json()["total"] == 1
    mismatch = await client.get("/api/v1/items", headers=auth_headers, params={"condition": "poor"})
    assert mismatch.json()["total"] == 0
    changed = await client.patch(
        f"/api/v1/items/{item['id']}",
        headers=auth_headers,
        json={"condition": "good", "season": ["all-season"]},
    )
    assert changed.status_code == 200
    assert changed.json()["season"] == changed.json()["tags"]["season"] == ["all-season"]
    for s in ("spring", "summer", "fall", "winter"):
        result = await client.get("/api/v1/items", headers=auth_headers, params={"season": s})
        assert result.json()["total"] == 1
    changed = await client.patch(
        f"/api/v1/items/{item['id']}",
        headers=auth_headers,
        json={"condition": None, "storage_location_id": None, "season": []},
    )
    assert changed.json()["condition"] is None
    assert changed.json()["storage_location_id"] is None
    assert changed.json()["season"] == changed.json()["tags"]["season"] == []


@pytest.mark.asyncio
async def test_locations_ownership_and_in_use(client, auth_headers, test_user, db_session):
    other = User(id=uuid4(), email=f"{uuid4()}@example.com", display_name="Other")
    db_session.add(other)
    await db_session.commit()
    response = await client.post("/api/v1/locations", json={"name": "卧室"}, headers=auth_headers)
    location_id = response.json()["id"]
    await db_session.commit()
    item = ClothingItem(
        user_id=test_user.id,
        type="shirt",
        image_path="test/a.jpg",
        status=ItemStatus.ready,
        storage_location_id=location_id,
    )
    db_session.add(item)
    await db_session.commit()
    from app.api.auth import create_access_token
    from app.models import UserIdentity

    identity = UserIdentity(user_id=other.id, provider="legacy", subject=str(other.id))
    db_session.add(identity)
    await db_session.commit()
    other_headers = {"Authorization": f"Bearer {create_access_token(other.id, identity.id)}"}
    assert (await client.get("/api/v1/locations", headers=other_headers)).json() == []
    assert (
        await client.patch(
            f"/api/v1/locations/{location_id}", json={"name": "stolen"}, headers=other_headers
        )
    ).status_code == 404
    assert (
        await client.post(
            "/api/v1/items",
            headers=other_headers,
            files={"image": ("a.jpg", photo(), "image/jpeg")},
            data={"type": "shirt", "storage_location_id": location_id, "skip_ai": "true"},
        )
    ).status_code == 404
    assert (
        await client.delete(f"/api/v1/locations/{location_id}", headers=auth_headers)
    ).status_code == 409


@pytest.mark.asyncio
async def test_manual_outfit_reverse_lookup_and_delete_guard(
    client, auth_headers, test_user, db_session
):
    items = [
        ClothingItem(
            user_id=test_user.id, type=t, image_path=f"test/{t}.jpg", status=ItemStatus.ready
        )
        for t in ("t-shirt", "shorts", "jacket")
    ]
    db_session.add_all(items)
    await db_session.commit()
    ids = [str(i.id) for i in items]
    created = await client.post(
        "/api/v1/outfits/studio",
        headers=auth_headers,
        json={"name": "夏日搭配", "items": ids[:2], "occasion": "casual"},
    )
    assert created.status_code == 201, created.text
    outfit_id = created.json()["id"]
    found = await client.get("/api/v1/outfits", headers=auth_headers, params={"item_id": ids[0]})
    assert [o["id"] for o in found.json()["outfits"]] == [outfit_id]
    absent = await client.get("/api/v1/outfits", headers=auth_headers, params={"item_id": ids[2]})
    assert absent.json()["total"] == 0
    assert (await client.delete(f"/api/v1/items/{ids[0]}", headers=auth_headers)).status_code == 409
    assert (
        await client.post(f"/api/v1/items/{ids[0]}/archive", headers=auth_headers, json={})
    ).status_code == 200
    assert (
        await client.delete(f"/api/v1/outfits/{outfit_id}", headers=auth_headers)
    ).status_code == 204
    assert (await client.delete(f"/api/v1/items/{ids[0]}", headers=auth_headers)).status_code == 204


@pytest.mark.asyncio
async def test_local_login_revocation_and_sync_bypass(client, db_session, monkeypatch):
    settings = get_settings()
    monkeypatch.setattr(settings, "local_auth_enabled", True)
    email = f"{uuid4()}@example.com"
    await provision(db_session, email, "a-secure-test-password", "Tester")
    await db_session.commit()
    wrong = await client.post("/api/v1/auth/login", json={"email": email, "password": "wrong"})
    assert wrong.status_code == 401
    login = await client.post(
        "/api/v1/auth/login", json={"email": email, "password": "a-secure-test-password"}
    )
    assert login.status_code == 200, login.text
    token = login.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}
    assert (await client.get("/api/v1/users/me", headers=headers)).status_code == 200
    assert (await client.get("/api/v1/auth/config")).json()["local_auth"] is True
    bypass = await client.post(
        "/api/v1/auth/sync",
        json={"external_id": "forged", "email": email, "display_name": "Tester"},
    )
    assert bypass.status_code == 401
    changed = await client.post(
        "/api/v1/auth/password",
        headers=headers,
        json={
            "current_password": "a-secure-test-password",
            "new_password": "another-secure-password",
        },
    )
    assert changed.status_code == 204, changed.text
    await db_session.commit()
    assert (await client.get("/api/v1/users/me", headers=headers)).status_code == 401
    assert (
        await client.post(
            "/api/v1/auth/login", json={"email": email, "password": "a-secure-test-password"}
        )
    ).status_code == 401
    assert (
        await client.post(
            "/api/v1/auth/login", json={"email": email, "password": "another-secure-password"}
        )
    ).status_code == 200

    await db_session.execute(delete(User).where(User.email == email))
    await db_session.commit()


def test_local_auth_disables_development_bypass():
    settings = Settings(debug=True, local_auth_enabled=True, secret_key="custom-secret")
    assert not settings.dev_mode
    assert settings.get_auth_mode() == "local"
    assert settings.validate_security() is None


@pytest.mark.asyncio
async def test_local_only_refuses_previous_development_tokens(client, auth_headers, monkeypatch):
    monkeypatch.setattr(get_settings(), "local_auth_enabled", True)
    assert (await client.get("/api/v1/items", headers=auth_headers)).status_code == 401
    assert (
        await client.post(
            "/api/v1/auth/login", json={"email": "missing@example.com", "password": "invalid"}
        )
    ).status_code == 401


@pytest.mark.asyncio
async def test_local_login_is_disabled_by_default(client):
    response = await client.post(
        "/api/v1/auth/login", json={"email": "missing@example.com", "password": "invalid"}
    )
    assert response.status_code == 404
