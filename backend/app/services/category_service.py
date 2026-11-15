"""Initial Chinese categories are copied once; subsequent edits belong to the user."""

import json
from pathlib import Path
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.category import ClothingCategory

DEFAULT_CATEGORIES: dict[str, str] = json.loads(
    (Path(__file__).parent.parent / "data" / "default_categories.json").read_text()
)


async def seed_categories(db: AsyncSession, user_id: UUID) -> None:
    db.add_all(
        [
            ClothingCategory(user_id=user_id, name=name, builtin_type=code)
            for code, name in DEFAULT_CATEGORIES.items()
        ]
    )
    await db.flush()
