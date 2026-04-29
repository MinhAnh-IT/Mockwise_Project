"""One-shot bootstrap: pull every ACTIVE behavioral + core question from the
question-bank service and (re)index it.

Used:
- once when the AI service is first deployed against an existing question bank
- as the recovery path if a Kafka event was lost (rare given AFTER_COMMIT
  publishing, but possible if the JVM crashes between commit and send).

We hit the existing admin list endpoints with status=ACTIVE so no new endpoint
is needed on the question-bank side. Inside the internal network these are
unauthenticated (the gateway gates admin auth, not the service itself); we
still send X-Internal-API-Key for parity with other internal calls.
"""
from __future__ import annotations

import logging
import time
from datetime import datetime, timezone
from typing import Any, Dict, List

import httpx

import config
from llm import embed_text
from models.selector.snapshot import QuestionSnapshot
from selector.repository import question_index as repo
from selector.retrieval.embedding import build_question_embedding_text

logger = logging.getLogger(__name__)

_PAGE_SIZE = 50


def _headers() -> Dict[str, str]:
    headers = {"Accept": "application/json"}
    if config.QUESTION_BANK_INTERNAL_API_KEY:
        headers["X-Internal-API-Key"] = config.QUESTION_BANK_INTERNAL_API_KEY
    return headers


def _unwrap_list(payload: Any) -> List[Dict[str, Any]]:
    """Unwrap either ApiListResponse {data:[...]} or a bare list."""
    if isinstance(payload, dict):
        for key in ("data", "items", "content"):
            if isinstance(payload.get(key), list):
                return payload[key]
    if isinstance(payload, list):
        return payload
    return []


async def _fetch_active(client: httpx.AsyncClient, path: str) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    page = 0
    while True:
        resp = await client.get(
            path,
            params={"status": "ACTIVE", "page": page, "size": _PAGE_SIZE},
        )
        resp.raise_for_status()
        items = _unwrap_list(resp.json())
        if not items:
            break
        out.extend(items)
        if len(items) < _PAGE_SIZE:
            break
        page += 1
    return out


def _behavioral_to_snapshot(item: Dict[str, Any]) -> QuestionSnapshot:
    return QuestionSnapshot(
        id=item["id"],
        type="BEHAVIORAL",
        text=item.get("text") or "",
        difficulty=item.get("difficulty") or "",
        tags=list(item.get("tags") or []),
        competency=item.get("competency"),
        expected_signals=list(item.get("expectedSignals") or []),
    )


def _core_to_snapshot(item: Dict[str, Any]) -> QuestionSnapshot:
    return QuestionSnapshot(
        id=item["id"],
        type="CORE_CONCEPTUAL",
        text=item.get("text") or "",
        difficulty=item.get("difficulty") or "",
        tags=list(item.get("tags") or []),
        domain=item.get("domain"),
        target_roles=list(item.get("targetRoles") or []),
        key_concepts=list(item.get("keyConcepts") or []),
        depth_expected=item.get("depthExpected"),
    )


async def _index_one(snapshot: QuestionSnapshot) -> bool:
    try:
        text = build_question_embedding_text(snapshot)
        embedding = embed_text(text)
        await repo.upsert_question(snapshot, embedding, datetime.now(tz=timezone.utc))
        return True
    except Exception:
        logger.exception("Failed to index question id=%s", snapshot.id)
        return False


async def reindex_all() -> Dict[str, Any]:
    start_ms = int(time.time() * 1000)

    async with httpx.AsyncClient(
        base_url=config.QUESTION_BANK_BASE_URL,
        headers=_headers(),
        timeout=30.0,
    ) as client:
        behavioral = await _fetch_active(client, "/api/v1/question-bank/admin/questions/behavioral")
        core = await _fetch_active(client, "/api/v1/question-bank/admin/questions/core")

    logger.info("Reindex: fetched %d behavioral + %d core active questions",
                len(behavioral), len(core))

    behavioral_ok = 0
    core_ok = 0
    failed = 0

    for item in behavioral:
        if await _index_one(_behavioral_to_snapshot(item)):
            behavioral_ok += 1
        else:
            failed += 1

    for item in core:
        if await _index_one(_core_to_snapshot(item)):
            core_ok += 1
        else:
            failed += 1

    duration_ms = int(time.time() * 1000) - start_ms
    return {
        "behavioral_indexed": behavioral_ok,
        "core_indexed": core_ok,
        "failed": failed,
        "duration_ms": duration_ms,
    }
