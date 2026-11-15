"""Exercise personal categories through uploads, outfits and the historical migration."""

from uuid import uuid4

import pytest
from sqlalchemy import select

from app.api.auth import create_access_token
from app.config import get_settings
from app.models import ClothingCategory, ClothingItem, User, UserIdentity
from app.services.local_auth import provision
from tests.migration_helpers import insert_historical_rows, migration_downgrade
from tests.test_personal_wardrobe import photo


@pytest.mark.asyncio
async def test_category_lifecycle_across_items_and_outfits(client, auth_headers, db_session):
    response = await client.post(
        "/api/v1/categories", json={"name": "  短袖  "}, headers=auth_headers
    )
    assert response.status_code == 201
    category = response.json()
    assert category["name"] == "短袖"
    created = await client.post(
        "/api/v1/items",
        headers=auth_headers,
        files={"image": ("shirt.jpg", photo(), "image/jpeg")},
        data={"category_id": category["id"], "skip_ai": "true", "season": ["spring", "summer"]},
    )
    assert created.status_code == 201, created.text
    item = created.json()
    assert item["category_id"] == category["id"]
    assert item["category_name"] == "短袖"
    outfit = await client.post(
        "/api/v1/outfits/studio",
        headers=auth_headers,
        json={
            "name": "周末",
            "items": [item["id"]],
            "occasion": "casual",
        },
    )
    assert outfit.status_code == 201, outfit.text
    assert outfit.json()["items"][0]["category_name"] == "短袖"
    renamed = await client.patch(
        f"/api/v1/categories/{category['id']}", headers=auth_headers, json={"name": "夏装"}
    )
    assert renamed.status_code == 200
    result = await client.get(
        "/api/v1/items", headers=auth_headers, params={"category_id": category["id"]}
    )
    assert result.json()["total"] == 1
    assert result.json()["items"][0]["category_name"] == "夏装"
    result = await client.get(f"/api/v1/outfits/{outfit.json()['id']}", headers=auth_headers)
    assert result.json()["items"][0]["category_name"] == "夏装"
    mismatch = await client.get(
        "/api/v1/items", headers=auth_headers, params={"category_id": str(uuid4())}
    )
    assert mismatch.json()["total"] == 0
    await client.post(f"/api/v1/items/{item['id']}/archive", headers=auth_headers, json={})
    await db_session.commit()
    response = await client.delete(f"/api/v1/categories/{category['id']}", headers=auth_headers)
    assert response.status_code == 409  # Archived items still count.
    await db_session.rollback()
    cleared = await client.patch(
        f"/api/v1/items/{item['id']}", headers=auth_headers, json={"category_id": None}
    )
    assert cleared.status_code == 200, cleared.text
    assert cleared.json()["category_id"] is None
    assert cleared.json()["category_name"] is None
    assert (
        await client.delete(f"/api/v1/categories/{category['id']}", headers=auth_headers)
    ).status_code == 204


@pytest.mark.asyncio
async def test_category_names_and_user_isolation(client, auth_headers, test_user, db_session):
    for name in (" ", "类" * 101):
        assert (
            await client.post("/api/v1/categories", headers=auth_headers, json={"name": name})
        ).status_code == 422
    created = await client.post("/api/v1/categories", headers=auth_headers, json={"name": "外套"})
    category_id = created.json()["id"]
    await db_session.commit()
    assert (
        await client.post("/api/v1/categories", headers=auth_headers, json={"name": " 外套 "})
    ).status_code == 409
    await db_session.rollback()
    other = User(id=uuid4(), email=f"{uuid4()}@example.com", display_name="Other")
    db_session.add(other)
    await db_session.flush()
    identity = UserIdentity(user_id=other.id, provider="legacy", subject=str(other.id))
    db_session.add(identity)
    await db_session.commit()
    other_headers = {"Authorization": f"Bearer {create_access_token(other.id, identity.id)}"}
    assert (await client.get("/api/v1/categories", headers=other_headers)).json() == []
    assert (
        await client.patch(
            f"/api/v1/categories/{category_id}", headers=other_headers, json={"name": "偷改"}
        )
    ).status_code == 404
    assert (
        await client.delete(f"/api/v1/categories/{category_id}", headers=other_headers)
    ).status_code == 404
    assert (
        await client.post(
            "/api/v1/items",
            headers=other_headers,
            files={"image": ("a.jpg", photo(), "image/jpeg")},
            data={"category_id": category_id, "skip_ai": "true"},
        )
    ).status_code == 404
    assert (
        await client.post("/api/v1/categories", headers=other_headers, json={"name": "外套"})
    ).status_code == 201


@pytest.mark.asyncio
async def test_personal_seasons_expand_legacy_alias(client, auth_headers, monkeypatch):
    monkeypatch.setattr(get_settings(), "personal_mode", True)
    created = await client.post(
        "/api/v1/items",
        headers=auth_headers,
        files={"image": ("a.jpg", photo(), "image/jpeg")},
        data={"skip_ai": "true", "season": ["all-season"]},
    )
    assert created.status_code == 201, created.text
    item = created.json()
    seasons = ["spring", "summer", "fall", "winter"]
    assert item["season"] == item["tags"]["season"] == seasons
    for patch in ({"season": ["summer", "winter"]}, {"tags": {"season": ["all-season"]}}):
        updated = await client.patch(
            f"/api/v1/items/{item['id']}", headers=auth_headers, json=patch
        )
        assert updated.status_code == 200, updated.text
        assert updated.json()["season"] == updated.json()["tags"]["season"]
    assert updated.json()["season"] == seasons


@pytest.mark.asyncio
async def test_new_account_categories_seed_only_once(db_session):
    email = f"{uuid4()}@example.com"
    user_id = await provision(db_session, email, "test-password-123456", "测试")
    categories = (
        await db_session.scalars(
            select(ClothingCategory).where(ClothingCategory.user_id == user_id)
        )
    ).all()
    assert any(c.name == "短裤" for c in categories)
    deleted = categories[0]
    deleted_name = deleted.name
    await db_session.delete(deleted)
    await db_session.flush()
    await provision(db_session, email, "test-password-654321", "测试")
    remaining = (
        await db_session.scalars(
            select(ClothingCategory).where(ClothingCategory.user_id == user_id)
        )
    ).all()
    assert deleted_name not in [c.name for c in remaining]
    assert len(remaining) == len(categories) - 1


@pytest.mark.asyncio
async def test_existing_category_and_seasons_migration(db_session, test_user):
    user_id = test_user.id
    item_id, unknown_id = uuid4(), uuid4()
    async with migration_downgrade(db_session, "b8e4d0f2a6c1"):
        await insert_historical_rows(
            db_session,
            ClothingItem(
                id=item_id,
                user_id=user_id,
                type="shirt",
                subtype="旧子类",
                image_path="old.jpg",
                season=["summer", "all-season"],
                tags={"season": ["all-season"], "size": "L"},
            ),
            ClothingItem(
                id=unknown_id,
                user_id=user_id,
                type="旧自定义类型",
                image_path="custom.jpg",
                season=["winter"],
                tags={"season": ["winter"]},
            ),
        )
        await db_session.commit()
    db_session.expire_all()
    item = await db_session.get(ClothingItem, item_id)
    assert item.category_name == "衬衫"
    assert item.season == item.tags["season"] == ["spring", "summer", "fall", "winter"]
    assert item.tags["size"] == "L"
    assert item.subtype == "旧子类"  # Kept privately for rollback; no longer shown in UI.
    unknown = await db_session.get(ClothingItem, unknown_id)
    assert unknown.category_name == "旧自定义类型"
    assert unknown.season == ["winter"]
