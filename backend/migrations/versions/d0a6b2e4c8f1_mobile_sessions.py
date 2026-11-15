"""Add revocable mobile sessions and rotating refresh tokens.

Revision ID: d0a6b2e4c8f1
Revises: c9f5a1d3b7e2
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "d0a6b2e4c8f1"
down_revision = "c9f5a1d3b7e2"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "mobile_sessions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("identity_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_mobile_sessions_user_id", "mobile_sessions", ["user_id"])
    op.create_index("ix_mobile_sessions_expires_at", "mobile_sessions", ["expires_at"])
    op.create_table(
        "mobile_refresh_tokens",
        sa.Column("token_hash", sa.String(64), primary_key=True),
        sa.Column(
            "session_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("mobile_sessions.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column("consumed_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_mobile_refresh_tokens_session_id", "mobile_refresh_tokens", ["session_id"])


def downgrade() -> None:
    op.drop_table("mobile_refresh_tokens")
    op.drop_table("mobile_sessions")
