"""Kafka producer for session-evaluation-completed / session-evaluation-failed.

Mirrors the per-answer evaluation_producer pattern — a single AIOKafkaProducer
started once per process, idempotent, acks=all. Kept as a separate producer
(rather than reusing the per-answer one) so the lifespan wiring is explicit
and a session-review outage does not interfere with per-answer publishing.
"""
from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Optional

from aiokafka import AIOKafkaProducer

import config
from models.session_events import (
    SessionEvaluationCompletedEvent,
    SessionEvaluationFailedEvent,
)

logger = logging.getLogger(__name__)

_producer: Optional[AIOKafkaProducer] = None


async def start_producer() -> None:
    global _producer
    if _producer is not None:
        return
    if not config.KAFKA_BOOTSTRAP_SERVERS:
        logger.warning("KAFKA_BOOTSTRAP_SERVERS not set — session-review producer disabled")
        return
    _producer = AIOKafkaProducer(
        bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8") if isinstance(k, str) else k,
        acks="all",
        enable_idempotence=True,
    )
    await _producer.start()
    logger.info("Session-review producer started")


async def stop_producer() -> None:
    global _producer
    if _producer is not None:
        await _producer.stop()
        _producer = None
        logger.info("Session-review producer stopped")


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


async def publish_completed(session_id: str, result: dict) -> None:
    if _producer is None:
        logger.warning("session-evaluation-completed not published (producer disabled): session=%s", session_id)
        return
    event = SessionEvaluationCompletedEvent(
        eventId=str(uuid.uuid4()),
        occurredAt=_now_iso(),
        sessionId=session_id,
        result=result,
    )
    await _producer.send_and_wait(
        config.KAFKA_TOPIC_SESSION_EVAL_COMPLETED,
        value=event.model_dump(),
        key=session_id,
    )
    logger.info("Published session-evaluation-completed: session=%s", session_id)


async def publish_failed(session_id: str, error: str, detail: Optional[str] = None) -> None:
    if _producer is None:
        logger.warning("session-evaluation-failed not published (producer disabled): session=%s", session_id)
        return
    event = SessionEvaluationFailedEvent(
        eventId=str(uuid.uuid4()),
        occurredAt=_now_iso(),
        sessionId=session_id,
        error=error,
        detail=detail,
    )
    await _producer.send_and_wait(
        config.KAFKA_TOPIC_SESSION_EVAL_FAILED,
        value=event.model_dump(),
        key=session_id,
    )
    logger.info("Published session-evaluation-failed: session=%s reason=%s", session_id, error)
