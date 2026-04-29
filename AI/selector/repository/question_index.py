"""CRUD + retrieval queries against the question_index table."""
from __future__ import annotations

import logging
from datetime import datetime
from typing import Any, List, Optional

import numpy as np

from models.selector.snapshot import QuestionSnapshot
from selector.repository.db import get_pool

logger = logging.getLogger(__name__)


async def upsert_question(snapshot: QuestionSnapshot, embedding: List[float], event_at: datetime) -> None:
    """Insert or update a question row and refresh its embedding."""
    pool = get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO question_index (
                id, type, text, difficulty, tags,
                competency, expected_signals,
                domain, target_roles, key_concepts, depth_expected,
                embedding, active, indexed_at, last_event_at
            ) VALUES (
                $1, $2, $3, $4, $5,
                $6, $7,
                $8, $9, $10, $11,
                $12, true, now(), $13
            )
            ON CONFLICT (id) DO UPDATE SET
                type             = EXCLUDED.type,
                text             = EXCLUDED.text,
                difficulty       = EXCLUDED.difficulty,
                tags             = EXCLUDED.tags,
                competency       = EXCLUDED.competency,
                expected_signals = EXCLUDED.expected_signals,
                domain           = EXCLUDED.domain,
                target_roles     = EXCLUDED.target_roles,
                key_concepts     = EXCLUDED.key_concepts,
                depth_expected   = EXCLUDED.depth_expected,
                embedding        = EXCLUDED.embedding,
                active           = true,
                indexed_at       = now(),
                last_event_at    = EXCLUDED.last_event_at
            """,
            snapshot.id,
            snapshot.type,
            snapshot.text,
            snapshot.difficulty,
            snapshot.tags,
            snapshot.competency,
            snapshot.expected_signals,
            snapshot.domain,
            snapshot.target_roles,
            snapshot.key_concepts,
            snapshot.depth_expected,
            np.array(embedding, dtype=np.float32),
            event_at,
        )


async def deactivate_question(question_id: str, event_at: datetime) -> None:
    """Mark question inactive — excluded from retrieval but retained for audit/replay."""
    pool = get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE question_index SET active=false, last_event_at=$2 WHERE id=$1",
            question_id,
            event_at,
        )


# ─── Idempotency log ──────────────────────────────────────────────────────────

async def is_event_processed(event_id: str) -> bool:
    pool = get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT 1 FROM processed_events WHERE event_id=$1", event_id
        )
        return row is not None


async def mark_event_processed(event_id: str, event_type: str, question_id: str) -> None:
    pool = get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO processed_events (event_id, event_type, question_id)
            VALUES ($1, $2, $3)
            ON CONFLICT (event_id) DO NOTHING
            """,
            event_id,
            event_type,
            question_id,
        )


# ─── Retrieval ────────────────────────────────────────────────────────────────

async def hybrid_search(
    *,
    interview_type: str,
    query_embedding: List[float],
    asked_question_ids: List[str],
    competency: Optional[str] = None,
    domain: Optional[str] = None,
    target_role: Optional[str] = None,
    difficulty: Optional[str] = None,
    limit: int = 5,
) -> List[dict]:
    """
    Hard filter (type, active, exclusions, optional competency/domain/role/difficulty)
    + cosine similarity rerank in pgvector.

    Returns rows ordered by distance ascending (most similar first).
    """
    clauses: List[str] = ["type = $1", "active = true"]
    params: List[Any] = [interview_type]
    next_idx = 2

    if asked_question_ids:
        clauses.append(f"id <> ALL(${next_idx}::text[])")
        params.append(asked_question_ids)
        next_idx += 1

    if competency:
        clauses.append(f"competency = ${next_idx}")
        params.append(competency)
        next_idx += 1

    if domain:
        clauses.append(f"domain = ${next_idx}")
        params.append(domain)
        next_idx += 1

    if target_role:
        clauses.append(f"${next_idx} = ANY(target_roles)")
        params.append(target_role)
        next_idx += 1

    if difficulty:
        clauses.append(f"difficulty = ${next_idx}")
        params.append(difficulty)
        next_idx += 1

    embedding_param_idx = next_idx
    params.append(np.array(query_embedding, dtype=np.float32))
    next_idx += 1

    limit_param_idx = next_idx
    params.append(limit)

    where_sql = " AND ".join(clauses)
    sql = f"""
        SELECT
            id, type, text, difficulty, tags,
            competency, expected_signals,
            domain, target_roles, key_concepts, depth_expected,
            (embedding <=> ${embedding_param_idx}) AS distance
        FROM question_index
        WHERE {where_sql}
        ORDER BY embedding <=> ${embedding_param_idx}
        LIMIT ${limit_param_idx}
    """

    pool = get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(sql, *params)
        return [dict(row) for row in rows]


async def count_active(interview_type: Optional[str] = None) -> int:
    pool = get_pool()
    async with pool.acquire() as conn:
        if interview_type:
            row = await conn.fetchrow(
                "SELECT count(*) AS c FROM question_index WHERE active=true AND type=$1",
                interview_type,
            )
        else:
            row = await conn.fetchrow(
                "SELECT count(*) AS c FROM question_index WHERE active=true"
            )
        return int(row["c"])
