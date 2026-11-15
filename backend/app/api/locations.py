from uuid import UUID

from fastapi import APIRouter, HTTPException
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from app.database import DbSession
from app.models.location import StorageLocation
from app.schemas.location import LocationResponse, LocationWrite
from app.utils.auth import CurrentUser

router = APIRouter(prefix="/locations", tags=["Storage locations"])


async def owned_location(db: DbSession, user_id: UUID, location_id: UUID) -> StorageLocation:
    location = await db.scalar(
        select(StorageLocation).where(
            StorageLocation.id == location_id, StorageLocation.user_id == user_id
        )
    )
    if location is None:
        raise HTTPException(404, "Storage location not found")
    return location


@router.get("", response_model=list[LocationResponse])
async def list_locations(db: DbSession, user: CurrentUser):
    return (
        await db.scalars(
            select(StorageLocation)
            .where(StorageLocation.user_id == user.id)
            .order_by(StorageLocation.name, StorageLocation.id)
        )
    ).all()


@router.post("", response_model=LocationResponse, status_code=201)
async def create_location(data: LocationWrite, db: DbSession, user: CurrentUser):
    location = StorageLocation(user_id=user.id, name=data.name)
    db.add(location)
    try:
        await db.flush()
    except IntegrityError:
        raise HTTPException(409, "A storage location with this name already exists") from None
    return location


@router.patch("/{location_id}", response_model=LocationResponse)
async def update_location(location_id: UUID, data: LocationWrite, db: DbSession, user: CurrentUser):
    location = await owned_location(db, user.id, location_id)
    location.name = data.name
    try:
        await db.flush()
    except IntegrityError:
        raise HTTPException(409, "A storage location with this name already exists") from None
    return location


@router.delete("/{location_id}", status_code=204)
async def delete_location(location_id: UUID, db: DbSession, user: CurrentUser):
    location = await owned_location(db, user.id, location_id)
    await db.delete(location)
    try:
        await db.flush()
    except IntegrityError:
        raise HTTPException(
            409, "Move the clothing items to another location before deleting"
        ) from None
