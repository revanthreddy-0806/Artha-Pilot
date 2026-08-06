"""Add risk_questionnaires table for the risk-appetite questionnaire.

Revision ID: 0004
Revises: 0003
Create Date: 2026-08-06
"""
from alembic import op
import sqlalchemy as sa

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "risk_questionnaires",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, unique=True),
        sa.Column("answers", sa.Text(), nullable=False),
        sa.Column("tolerance_score", sa.Integer(), nullable=False),
        sa.Column("tolerance_band", sa.String(16), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_risk_questionnaires_user_id", "risk_questionnaires", ["user_id"], unique=True)


def downgrade() -> None:
    op.drop_index("ix_risk_questionnaires_user_id", table_name="risk_questionnaires")
    op.drop_table("risk_questionnaires")