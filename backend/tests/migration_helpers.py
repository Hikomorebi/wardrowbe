import os
import subprocess
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from uuid import uuid4

from sqlalchemy import MetaData, Table, inspect
from sqlalchemy.ext.asyncio import AsyncSession


def alembic(*args: str) -> None:
    env = {**os.environ, "DATABASE_URL": os.environ["TEST_DATABASE_URL"]}
    result = subprocess.run(
        ["python", "-m", "alembic", *args], env=env, capture_output=True, text=True, timeout=120
    )
    if result.returncode != 0:
        raise RuntimeError(f"alembic {' '.join(args)} failed:\n{result.stderr[-4000:]}")


@asynccontextmanager
async def migration_downgrade(db_session: AsyncSession, revision: str) -> AsyncIterator[None]:
    # Migrations lock users, so the test's own transaction must end before each alembic run or
    # the migration waits on it until lock_timeout; a failed body leaves it idle or aborted.
    await db_session.commit()
    alembic("downgrade", revision)
    try:
        yield
    finally:
        await db_session.rollback()
        alembic("upgrade", "head")


async def insert_historical_rows(db_session: AsyncSession, *objects) -> None:
    """Seed a historical schema using its actual columns, not the current ORM's new fields."""
    connection = await db_session.connection()
    for obj in objects:
        table = await connection.run_sync(
            lambda conn, name=obj.__tablename__: Table(name, MetaData(), autoload_with=conn)
        )
        values = {key: value for key, value in inspect(obj).dict.items() if key in table.c}
        if obj.__tablename__ == "clothing_items":
            values.setdefault("tags", {})
        if "id" in table.c and "id" not in values:
            values["id"] = uuid4()
        await db_session.execute(table.insert().values(**values))
