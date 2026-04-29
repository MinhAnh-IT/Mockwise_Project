"""Unified AI service — evaluation graph + question selector RAG.

Endpoints
- GET  /health
- POST /evaluate             ← evaluation graph (live coding / behavioral / conceptual)
- POST /generate-testcases   ← LeetCode-aware testcase generator
- GET  /leetcode/fetch       ← LeetCode problem fetcher
- POST /selector/next-question
- POST /selector/admin/reindex

The selector features depend on Postgres + Kafka. When DATABASE_URL or
KAFKA_BOOTSTRAP_SERVERS are not configured the lifespan skips the DB pool
and consumer task respectively, and the selector endpoints will return
503. This lets the evaluation half run standalone in environments that
don't need RAG.
"""
from __future__ import annotations

import asyncio
import logging
import os
import time
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Security
from fastapi.responses import JSONResponse
from fastapi.security.api_key import APIKeyHeader

import config
from main import (
    evaluate,
    generate_testcases,
    get_generator_graph,
    get_graph,
)
from generator.leetcode_fetcher import (
    LeetCodeFetchError,
    LeetCodeNotFoundError,
    LeetCodePremiumError,
    fetch_leetcode_problem,
)
from models.selector.api import (
    NextQuestionRequest,
    NextQuestionResponse,
    ReindexResponse,
)

logger = logging.getLogger(__name__)
logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))


# ─── Auth ─────────────────────────────────────────────────────────────────────

_API_KEY = os.getenv("SERVICE_API_KEY", "")
_api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


def _require_api_key(api_key: str = Security(_api_key_header)) -> None:
    if _API_KEY and api_key != _API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")


# ─── Lifespan ─────────────────────────────────────────────────────────────────

_consumer_task: Optional[asyncio.Task] = None
_consumer_stop: Optional[asyncio.Event] = None
_selector_enabled = False
_start_time: float = 0.0


@asynccontextmanager
async def lifespan(_: FastAPI):
    global _consumer_task, _consumer_stop, _selector_enabled, _start_time
    _start_time = time.time()

    if config.DATABASE_URL:
        from selector.repository.db import init_pool
        try:
            await init_pool()
            _selector_enabled = True
            logger.info("Selector DB pool initialised")
        except Exception as exc:
            # Fail-soft: keep the service up so /evaluate and /generate-testcases
            # still work. Selector endpoints will return 503 until the DB is
            # reachable. Common causes: DB does not exist, pgvector not
            # installed, network down. Operator must run the migration and
            # restart the container to enable selector.
            logger.error(
                "Selector DB pool init failed (%s) — selector endpoints will return 503 "
                "until DATABASE_URL is reachable and the migration has been run.",
                exc,
            )
    else:
        logger.warning(
            "DATABASE_URL not set — selector endpoints will be disabled"
        )

    if _selector_enabled and config.KAFKA_BOOTSTRAP_SERVERS:
        from selector.messaging.consumer import run_consumer
        _consumer_stop = asyncio.Event()
        _consumer_task = asyncio.create_task(run_consumer(_consumer_stop))
        logger.info("Kafka consumer task started")
    else:
        logger.warning(
            "KAFKA_BOOTSTRAP_SERVERS not set or selector disabled — consumer not started"
        )

    logger.info("AI service started")
    try:
        yield
    finally:
        if _consumer_stop is not None:
            _consumer_stop.set()
        if _consumer_task is not None:
            try:
                await asyncio.wait_for(_consumer_task, timeout=10.0)
            except asyncio.TimeoutError:
                logger.warning("Kafka consumer did not stop within 10s; cancelling")
                _consumer_task.cancel()
        if _selector_enabled:
            from selector.repository.db import close_pool
            await close_pool()
        logger.info("AI service stopped")


app = FastAPI(title="AI Service", version="1.0.0", lifespan=lifespan)


# ─── Health ───────────────────────────────────────────────────────────────────

@app.get("/health")
async def health():
    checks = {}
    overall = "healthy"

    if config.GOOGLE_API_KEY:
        checks["google_api_key"] = "configured"
    else:
        checks["google_api_key"] = "missing"
        overall = "unhealthy"

    try:
        get_graph()
        checks["evaluation_graph"] = "ok"
    except Exception as e:
        checks["evaluation_graph"] = f"error: {e}"
        overall = "unhealthy"

    try:
        get_generator_graph()
        checks["generator_graph"] = "ok"
    except Exception as e:
        checks["generator_graph"] = f"error: {e}"
        overall = "unhealthy"

    if _selector_enabled:
        try:
            from selector.repository.question_index import count_active
            active = await count_active()
            checks["selector_index_active_count"] = active
        except Exception as e:
            checks["selector_index_active_count"] = f"error: {e}"
            overall = "unhealthy"
    else:
        checks["selector"] = "disabled (no DATABASE_URL)"

    body = {
        "status": overall,
        "uptime_seconds": round(time.time() - _start_time, 1),
        "model": config.MODEL_NAME,
        "embedding_model": config.EMBEDDING_MODEL,
        "evaluator_version": config.EVALUATOR_VERSION,
        "selector_version": config.SELECTOR_VERSION,
        "checks": checks,
    }
    return JSONResponse(content=body, status_code=200 if overall == "healthy" else 503)


# ─── Evaluation graph endpoints ───────────────────────────────────────────────

@app.post("/evaluate")
def evaluate_interview(payload: dict, _: None = Security(_require_api_key)):
    try:
        return evaluate(payload)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/generate-testcases")
def generate_testcases_endpoint(payload: dict, _: None = Security(_require_api_key)):
    try:
        result = generate_testcases(payload)
        if "error" in result:
            raise HTTPException(status_code=422, detail=result)
        return result
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/leetcode/fetch")
def leetcode_fetch(
    url: str = Query(..., description="LeetCode problem URL or bare slug"),
    session_cookie: Optional[str] = Query(
        None, description="Optional LEETCODE_SESSION cookie for premium problems"
    ),
    _: None = Security(_require_api_key),
):
    try:
        problem = fetch_leetcode_problem(url, session_cookie=session_cookie)
        return problem.model_dump()
    except LeetCodeNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except LeetCodePremiumError as e:
        raise HTTPException(status_code=403, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except LeetCodeFetchError as e:
        raise HTTPException(status_code=502, detail=str(e))


# ─── Selector endpoints ───────────────────────────────────────────────────────

def _require_selector() -> None:
    if not _selector_enabled:
        raise HTTPException(
            status_code=503,
            detail="Selector is disabled (DATABASE_URL not configured)",
        )


@app.post("/selector/next-question", response_model=NextQuestionResponse)
async def next_question(req: NextQuestionRequest, _: None = Security(_require_api_key)):
    _require_selector()
    from selector.retrieval.selector import select_next_question
    try:
        return await select_next_question(req)
    except RuntimeError as exc:
        raise HTTPException(status_code=404, detail=str(exc))
    except Exception as exc:
        logger.exception("next_question failed")
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/selector/admin/reindex", response_model=ReindexResponse)
async def admin_reindex(_: None = Security(_require_api_key)):
    _require_selector()
    from selector.retrieval.reindex import reindex_all
    try:
        return await reindex_all()
    except Exception as exc:
        logger.exception("reindex failed")
        raise HTTPException(status_code=500, detail=str(exc))
