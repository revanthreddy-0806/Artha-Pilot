"""Allow manual transactions without an imported statement.

Revision ID: 0006
Revises: 0005
Create Date: 2026-11-09
"""
from alembic import op
import sqlalchemy as sa

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("transactions") as batch_op:
        batch_op.alter_column("statement_id", existing_type=sa.Integer(), nullable=True)


def downgrade() -> None:
    with op.batch_alter_table("transactions") as batch_op:
        batch_op.alter_column("statement_id", existing_type=sa.Integer(), nullable=False)