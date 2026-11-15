from uuid import UUID

from fastapi import APIRouter, HTTPException
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from app.database import DbSession
from app.models.category import ClothingCategory
from app.schemas.category import CategoryResponse, CategoryWrite
from app.utils.auth import CurrentUser

router = APIRouter(prefix="/categories", tags=["Clothing categories"])


async def owned_category(db: DbSession, user_id: UUID, category_id: UUID) -> ClothingCategory:
    category = await db.scalar(
        select(ClothingCategory).where(
            ClothingCategory.id == category_id, ClothingCategory.user_id == user_id
        )
    )
    if category is None:
        raise HTTPException(404, "Category not found")
    return category


@router.get("", response_model=list[CategoryResponse])
async def list_categories(db: DbSession, user: CurrentUser):
    return (
        await db.scalars(
            select(ClothingCategory)
            .where(ClothingCategory.user_id == user.id)
            .order_by(ClothingCategory.name, ClothingCategory.id)
        )
    ).all()


@router.post("", response_model=CategoryResponse, status_code=201)
async def create_category(data: CategoryWrite, db: DbSession, user: CurrentUser):
    category = ClothingCategory(user_id=user.id, name=data.name)
    db.add(category)
    try:
        await db.flush()
    except IntegrityError:
        raise HTTPException(409, "A category with this name already exists") from None
    return category


@router.patch("/{category_id}", response_model=CategoryResponse)
async def update_category(category_id: UUID, data: CategoryWrite, db: DbSession, user: CurrentUser):
    category = await owned_category(db, user.id, category_id)
    category.name = data.name
    try:
        await db.flush()
    except IntegrityError:
        raise HTTPException(409, "A category with this name already exists") from None
    return category


@router.delete("/{category_id}", status_code=204)
async def delete_category(category_id: UUID, db: DbSession, user: CurrentUser):
    category = await owned_category(db, user.id, category_id)
    await db.delete(category)
    try:
        await db.flush()
    except IntegrityError:
        raise HTTPException(
            409, "Reassign or clear the clothing category before deleting"
        ) from None
