"""Personal wardrobe fields, storage locations and local authentication."""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "b8e4d0f2a6c1"
down_revision: str | None = "a7d3c9e1f5b2"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "storage_locations",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("name", sa.String(100), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.UniqueConstraint("user_id", "name", name="uq_storage_location_name"),
    )
    op.create_index("ix_storage_locations_user_id", "storage_locations", ["user_id"])
    op.add_column("clothing_items", sa.Column("condition", sa.String(20)))
    op.create_check_constraint(
        "ck_item_condition", "clothing_items", "condition IN ('excellent', 'good', 'fair', 'poor')"
    )
    op.add_column(
        "clothing_items",
        sa.Column(
            "storage_location_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("storage_locations.id", ondelete="RESTRICT"),
        ),
    )
    op.create_index(
        "ix_clothing_items_storage_location_id", "clothing_items", ["storage_location_id"]
    )
    op.create_index("ix_outfit_items_item_id", "outfit_items", ["item_id"])
    op.add_column("users", sa.Column("password_hash", sa.Text()))
    op.drop_constraint("ck_user_identities_provider", "user_identities", type_="check")
    op.create_check_constraint(
        "ck_user_identities_provider",
        "user_identities",
        "provider IN ('legacy', 'dev', 'oidc', 'forward_auth', 'local')",
    )


def downgrade() -> None:
    # Refuse to silently remove the only way a local account can sign in.
    op.execute(
        "DO $$ BEGIN IF EXISTS (SELECT 1 FROM user_identities WHERE provider = 'local') THEN RAISE EXCEPTION 'Local accounts exist; restore the pre-upgrade backup to downgrade'; END IF; END $$"
    )
    op.drop_constraint("ck_user_identities_provider", "user_identities", type_="check")
    op.create_check_constraint(
        "ck_user_identities_provider",
        "user_identities",
        "provider IN ('legacy', 'dev', 'oidc', 'forward_auth')",
    )
    op.drop_column("users", "password_hash")
    op.drop_index("ix_outfit_items_item_id", "outfit_items")
    op.drop_index("ix_clothing_items_storage_location_id", "clothing_items")
    op.drop_column("clothing_items", "storage_location_id")
    op.drop_constraint("ck_item_condition", "clothing_items", type_="check")
    op.drop_column("clothing_items", "condition")
    op.drop_table("storage_locations")
