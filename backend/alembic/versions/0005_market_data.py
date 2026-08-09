"""Add market_data_snapshots for the daily-refresh reference prices.

Revision ID: 0005
Revises: 0004
Create Date: 2026-08-09
"""
from alembic import op
import sqlalchemy as sa

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "market_data_snapshots",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("symbol", sa.String(32), nullable=False, unique=True),
        sa.Column("name", sa.String(64), nullable=False),
        sa.Column("price", sa.Float(), nullable=False),
        sa.Column("change_pct", sa.Float(), nullable=True),
        sa.Column("as_of", sa.DateTime(timezone=True), nullable=False),
        sa.Column("source", sa.String(64), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_market_data_snapshots_symbol", "market_data_snapshots", ["symbol"], unique=True)


def downgrade() -> None:
    op.drop_index("ix_market_data_snapshots_symbol", table_name="market_data_snapshots")
    op.drop_table("market_data_snapshots")