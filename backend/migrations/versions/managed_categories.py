"""User-managed clothing categories and four individual seasons."""

import json

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "c9f5a1d3b7e2"
down_revision: str | None = "b8e4d0f2a6c1"
branch_labels = None
depends_on = None

DEFAULTS = {
    "accessories": "配饰",
    "bag": "包",
    "belt": "腰带",
    "blazer": "西装外套",
    "blouse": "女士衬衫",
    "bodysuit": "连体紧身衣",
    "boots": "靴子",
    "camisole": "吊带背心",
    "cardigan": "开衫",
    "coat": "大衣",
    "dress": "连衣裙",
    "flats": "平底鞋",
    "hat": "帽子",
    "heels": "高跟鞋",
    "hoodie": "卫衣",
    "jacket": "夹克",
    "jeans": "牛仔裤",
    "jumpsuit": "连体裤",
    "leggings": "打底裤",
    "overalls": "背带裤",
    "pants": "长裤",
    "polo": "Polo 衫",
    "poncho": "斗篷",
    "sandals": "凉鞋",
    "scarf": "围巾",
    "shirt": "衬衫",
    "shoes": "鞋子",
    "shorts": "短裤",
    "skirt": "半裙",
    "sneakers": "运动鞋",
    "socks": "袜子",
    "suit": "西装",
    "sweater": "毛衣",
    "sweatshirt": "圆领卫衣",
    "t-shirt": "T恤",
    "tank-top": "背心",
    "tie": "领带",
    "tights": "连裤袜",
    "top": "上装",
    "vest": "马甲",
    "watch": "手表",
}


def upgrade() -> None:
    op.create_table(
        "clothing_categories",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("name", sa.String(100), nullable=False),
        sa.Column("builtin_type", sa.String(50)),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.UniqueConstraint("user_id", "name", name="uq_clothing_category_name"),
    )
    op.create_index("ix_clothing_categories_user_id", "clothing_categories", ["user_id"])
    op.add_column("clothing_items", sa.Column("category_id", postgresql.UUID(as_uuid=True)))
    op.create_foreign_key(
        "fk_item_category",
        "clothing_items",
        "clothing_categories",
        ["category_id"],
        ["id"],
        ondelete="RESTRICT",
    )
    op.create_index("ix_clothing_items_category_id", "clothing_items", ["category_id"])
    connection = op.get_bind()
    defaults = {"defaults": json.dumps(DEFAULTS, ensure_ascii=False)}
    connection.execute(
        sa.text("""
        INSERT INTO clothing_categories (id, user_id, name, builtin_type)
        SELECT gen_random_uuid(), users.id, labels.value, labels.key
        FROM users CROSS JOIN jsonb_each_text(CAST(:defaults AS jsonb)) AS labels
    """),
        defaults,
    )
    # Preserve older types outside the vocabulary, reusing a translated category
    # if the old value already equals its Chinese label.
    connection.execute(
        sa.text("""
        INSERT INTO clothing_categories (id, user_id, name, builtin_type)
        SELECT gen_random_uuid(), user_id, type, type FROM (
            SELECT DISTINCT user_id, type FROM clothing_items
            WHERE type != 'unknown' AND NOT (CAST(:defaults AS jsonb) ? type)
        ) AS old_types
        ON CONFLICT (user_id, name) DO NOTHING
    """),
        defaults,
    )
    connection.execute(
        sa.text("""
        UPDATE clothing_items AS item SET category_id = category.id
        FROM clothing_categories AS category
        WHERE item.user_id = category.user_id AND (
            item.type = category.builtin_type OR (
                item.type = category.name AND NOT (CAST(:defaults AS jsonb) ? item.type)
            )
        )
    """),
        defaults,
    )
    op.execute("""
        UPDATE clothing_items
        SET season = ARRAY['spring', 'summer', 'fall', 'winter']::varchar[],
            tags = jsonb_set(COALESCE(tags, '{}'::jsonb), '{season}', '["spring", "summer", "fall", "winter"]'::jsonb)
        WHERE 'all-season' = ANY(season) OR tags->'season' @> '["all-season"]'::jsonb
    """)


def downgrade() -> None:
    op.drop_index("ix_clothing_items_category_id", "clothing_items")
    op.drop_constraint("fk_item_category", "clothing_items", type_="foreignkey")
    op.drop_column("clothing_items", "category_id")
    op.drop_table("clothing_categories")
