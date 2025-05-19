"""Initial schema"""

from alembic import op
import sqlalchemy as sa

revision = '001'
down_revision = None
branch_labels = None
depends_on = None

def upgrade() -> None:
    op.create_table(
        'bivariate_axis_overrides',
        sa.Column('numerator_id', sa.UUID(), nullable=False),
        sa.Column('denominator_id', sa.UUID(), nullable=False),
        sa.Column('label', sa.Text()),
        sa.Column('min', sa.Float()),
        sa.Column('p25', sa.Float()),
        sa.Column('p75', sa.Float()),
        sa.Column('max', sa.Float()),
        sa.Column('min_label', sa.Text()),
        sa.Column('p25_label', sa.Text()),
        sa.Column('p75_label', sa.Text()),
        sa.Column('max_label', sa.Text()),
        sa.Column('owner', sa.Text()),
        sa.PrimaryKeyConstraint('numerator_id', 'denominator_id')
    )

    op.create_table(
        'bivariate_indicators_metadata',
        sa.Column('internal_id', sa.UUID(), primary_key=True),
        sa.Column('param_label', sa.Text()),
        sa.Column('direction', sa.JSON()),
        sa.Column('external_id', sa.UUID()),
    )


def downgrade() -> None:
    op.drop_table('bivariate_indicators_metadata')
    op.drop_table('bivariate_axis_overrides')
