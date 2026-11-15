import asyncio
import hashlib
from datetime import UTC, datetime
from uuid import UUID, uuid4

from fastapi import HTTPException
from pwdlib import PasswordHash
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import User, UserIdentity, UserPreference
from app.schemas.email import normalise_email
from app.utils.rate_limit import check_rate_limit

passwords = PasswordHash.recommended()
DUMMY_HASH = passwords.hash("unused-password-for-timing-equalisation")


async def verify_password(password: str, encoded: str | None) -> bool:
    # Hash work is CPU intensive and must not block the API event loop.
    return await asyncio.to_thread(passwords.verify, password, encoded or DUMMY_HASH)


async def authenticate(db: AsyncSession, email: str, password: str) -> tuple[User, UserIdentity]:
    email = normalise_email(email)
    key = hashlib.sha256(email.encode()).hexdigest()
    await check_rate_limit(f"rate_limit:local_login:account:{key}", 10, 60)
    row = (
        await db.execute(
            select(User, UserIdentity)
            .join(UserIdentity, UserIdentity.user_id == User.id)
            .where(User.email == email, UserIdentity.provider == "local")
        )
    ).first()
    user, identity = row if row else (None, None)
    valid = await verify_password(password, user.password_hash if user else None)
    if not valid or user is None or not user.is_active or not user.password_hash:
        raise HTTPException(401, "Invalid email or password")
    user.last_login_at = datetime.now(UTC)
    return user, identity


async def set_password(db: AsyncSession, user: User, password: str) -> None:
    user.password_hash = await asyncio.to_thread(passwords.hash, password)
    identity = await db.scalar(
        select(UserIdentity)
        .where(UserIdentity.user_id == user.id, UserIdentity.provider == "local")
        .with_for_update()
    )
    if identity:
        # Every token is tied to this ID: rotate it to revoke all previous sessions.
        identity.id = uuid4()
    else:
        db.add(UserIdentity(user_id=user.id, provider="local", subject=str(user.id)))
    await db.flush()


async def provision(db: AsyncSession, email: str, password: str, name: str) -> UUID:
    email = normalise_email(email)
    user = await db.scalar(select(User).where(User.email == email).with_for_update())
    if user is None:
        user = User(
            email=email,
            display_name=name,
            locale="zh-CN",
            timezone="Asia/Shanghai",
            onboarding_completed=True,
            email_verified=False,
        )
        db.add(user)
        await db.flush()
        db.add(UserPreference(user_id=user.id))
    await set_password(db, user, password)
    return user.id
