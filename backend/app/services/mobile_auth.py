import hashlib
import secrets
from datetime import UTC, datetime, timedelta

from fastapi import HTTPException
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.auth import create_access_token
from app.models.identity import UserIdentity
from app.models.mobile_session import MobileRefreshToken, MobileSession
from app.models.user import User
from app.services.local_auth import authenticate

SESSION_LIFETIME = timedelta(days=30)
ACCESS_LIFETIME = timedelta(hours=1)


def refresh_hash(secret: str) -> str:
    return hashlib.sha256(secret.encode()).hexdigest()


def invalid_session(code: str = "MOBILE_SESSION_INVALID") -> HTTPException:
    return HTTPException(
        401,
        {"error_code": code, "message": "Mobile session expired or revoked. Sign in again."},
        headers={"Cache-Control": "no-store", "WWW-Authenticate": "Bearer"},
    )


async def issue_tokens(db: AsyncSession, session: MobileSession, user: User) -> dict:
    now = datetime.now(UTC)
    lifetime = min(ACCESS_LIFETIME, session.expires_at - now)
    secret = secrets.token_urlsafe(48)
    db.add(MobileRefreshToken(token_hash=refresh_hash(secret), session_id=session.id))
    await db.flush()
    return {
        "access_token": create_access_token(
            user.id, session.identity_id, lifetime, mobile_session_id=session.id
        ),
        "refresh_token": secret,
        "token_type": "bearer",
        "expires_in": max(1, int(lifetime.total_seconds())),
        "session_expires_at": session.expires_at,
        "user": {"id": user.id, "email": user.email, "display_name": user.display_name},
    }


async def login(db: AsyncSession, email: str, password: str) -> dict:
    user, identity = await authenticate(db, email, password)
    now = datetime.now(UTC)
    # History is only useful while its session can still be used. Cascades remove token hashes.
    await db.execute(
        delete(MobileSession).where(
            MobileSession.user_id == user.id, MobileSession.expires_at <= now
        )
    )
    session = MobileSession(
        user_id=user.id, identity_id=identity.id, expires_at=now + SESSION_LIFETIME
    )
    db.add(session)
    await db.flush()
    return await issue_tokens(db, session, user)


async def locked_session(db: AsyncSession, secret: str) -> MobileSession | None:
    # Every refresh/logout for a family takes the same row lock before inspecting token usage.
    return await db.scalar(
        select(MobileSession)
        .join(MobileRefreshToken, MobileRefreshToken.session_id == MobileSession.id)
        .where(MobileRefreshToken.token_hash == refresh_hash(secret))
        .with_for_update(of=MobileSession)
        .execution_options(populate_existing=True)
    )


async def refresh(db: AsyncSession, secret: str) -> dict:
    session = await locked_session(db, secret)
    now = datetime.now(UTC)
    if session is None or session.revoked_at or session.expires_at <= now:
        raise invalid_session()
    token = await db.get(MobileRefreshToken, refresh_hash(secret), populate_existing=True)
    if token.consumed_at:
        session.revoked_at = now
        # Persist revocation even though the request ends with an exception/rollback.
        await db.commit()
        raise invalid_session("MOBILE_REFRESH_REPLAY")
    user = await db.scalar(
        select(User)
        .join(UserIdentity, UserIdentity.user_id == User.id)
        .where(
            User.id == session.user_id,
            User.is_active.is_(True),
            UserIdentity.id == session.identity_id,
            UserIdentity.provider == "local",
        )
    )
    if user is None or not user.password_hash:
        session.revoked_at = now
        await db.commit()
        raise invalid_session()
    token.consumed_at = now
    return await issue_tokens(db, session, user)


async def logout(db: AsyncSession, secret: str) -> None:
    # A valid old token can also end its own family; never revoke based on an unverified ID.
    session = await locked_session(db, secret)
    if session and session.revoked_at is None:
        session.revoked_at = datetime.now(UTC)
