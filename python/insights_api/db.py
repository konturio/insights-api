import asyncpg
import os


async def _init_connection(conn: asyncpg.Connection):
    """Set common connection options."""
    # Return rows as dictionaries to avoid manual mapping
    conn.row_factory = dict

_pool = None

async def get_pool():
    global _pool
    if _pool is None:
        _pool = await asyncpg.create_pool(
            os.getenv("DATABASE_URL", "postgresql://localhost/postgres"),
            init=_init_connection,
        )
    return _pool
