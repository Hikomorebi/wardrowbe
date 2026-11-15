"""Interactive account provisioning: python -m app.manage [create-user|reset-password]."""

import argparse
import asyncio
from getpass import getpass

from sqlalchemy import select

from app.api.local_auth import PasswordChange
from app.database import async_session_maker, engine
from app.models import User
from app.schemas.email import normalise_email
from app.services.local_auth import provision


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["create-user", "reset-password"])
    args = parser.parse_args()
    email = normalise_email(input("Email: ").strip())
    password = getpass("Password (12-128 characters): ")
    PasswordChange(current_password="validation", new_password=password)
    if password != getpass("Repeat password: "):
        raise SystemExit("Passwords do not match")
    async with async_session_maker() as db:
        user = await db.scalar(select(User).where(User.email == email))
        if args.action == "create-user" and user:
            raise SystemExit("Account already exists; use reset-password")
        if args.action == "reset-password" and not user:
            raise SystemExit("Account not found")
        name = user.display_name if user else input("Display name: ").strip() or email.split("@")[0]
        await provision(db, email, password, name[:100])
        await db.commit()
    await engine.dispose()
    print("Account saved. Previous local sessions have been revoked.")


if __name__ == "__main__":
    asyncio.run(main())
