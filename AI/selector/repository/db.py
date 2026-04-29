"""Asyncpg pool + pgvector codec registration.

Lifecycle:
- `init_pool()` is called once during FastAPI startup.
- `close_pool()` on shutdown.
- `get_pool()` is the only accessor used by the rest of the code.
"""
from __future__ import annotations

import logging
from typing import Optional

import asyncpg
from pgvector.asyncpg import register_vector

import config

logger = logging.getLogger(__name__)

_pool: Optional[asyncpg.Pool] = None


async def _init_connection(conn: asyncpg.Connection) -> None:
    """Register pgvector codec on every new connection in the pool."""
    await register_vector(conn)


async def init_pool() -> asyncpg.Pool:
    global _pool
    if _pool is not None:
        return _pool

    if not config.DATABASE_URL:
        raise RuntimeError("DATABASE_URL is not configured")

    logger.info("Initialising asyncpg pool min=%d max=%d",
                config.DB_POOL_MIN_SIZE, config.DB_POOL_MAX_SIZE)
    _pool = await asyncpg.create_pool(
        dsn=config.DATABASE_URL,
        min_size=config.DB_POOL_MIN_SIZE,
        max_size=config.DB_POOL_MAX_SIZE,
        init=_init_connection,
    )
    return _pool


async def close_pool() -> None:
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None


def get_pool() -> asyncpg.Pool:
    if _pool is None:
        raise RuntimeError("DB pool is not initialised. Did the FastAPI startup hook run?")
    return _pool
