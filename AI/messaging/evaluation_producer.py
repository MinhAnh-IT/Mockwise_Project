"""Kafka producer for evaluation-completed / evaluation-failed.

A single shared AIOKafkaProducer is started once per process during the
FastAPI lifespan and reused — aiokafka guidance is to never create one
per message. `producer_started` is checked instead of constructing a new
client lazily, so misconfiguration is loud instead of silently no-op.
"""
from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Optional

from aiokafka import AIOKafkaProducer

import config
from models.evaluation_events import EvaluationCompletedEvent, EvaluationFailedEvent

logger = logging.getLogger(__name__)

_producer: Optional[AIOKafkaProducer] = None


async def start_producer() -> None:
    global _producer
    if _producer is not None:
        return
    if not config.KAFKA_BOOTSTRAP_SERVERS:
        logger.warning("KAFKA_BOOTSTRAP_SERVERS not set — evaluation producer disabled")
        return
    _producer = AIOKafkaProducer(
        bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8") if isinstance(k, str) else k,
        # acks=all for at-least-once: we only ack the consumer offset after
        # the producer commits, so a flake mid-publish becomes a redelivery,
        # not a silent drop.
        acks="all",
        enable_idempotence=True,
    )
    await _producer.start()
    logger.info("Evaluation producer started")


async def stop_producer() -> None:
    global _producer
    if _producer is not None:
        await _producer.stop()
        _producer = None
        logger.info("Evaluation producer stopped")


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


async def publish_completed(
    answer_id: str,
    session_id: str,
    question_id: str,
    interview_type: str,
    result: dict,
) -> None:
    if _producer is None:
        logger.warning("evaluation-completed not published (producer disabled): answer=%s", answer_id)
        return
    event = EvaluationCompletedEvent(
        eventId=str(uuid.uuid4()),
        occurredAt=_now_iso(),
        answerId=answer_id,
        sessionId=session_id,
        questionId=question_id,
        interviewType=interview_type,
        result=result,
    )
    # Key by answer_id so all events for one answer hit the same partition,
    # giving the consumer per-answer ordering for free.
    await _producer.send_and_wait(
        config.KAFKA_TOPIC_EVALUATION_COMPLETED,
        value=event.model_dump(),
        key=answer_id,
    )
    logger.info("Published evaluation-completed: answer=%s session=%s", answer_id, session_id)


async def publish_failed(
    answer_id: str,
    session_id: str,
    question_id: str,
    error: str,
    detail: Optional[str] = None,
) -> None:
    if _producer is None:
        logger.warning("evaluation-failed not published (producer disabled): answer=%s", answer_id)
        return
    event = EvaluationFailedEvent(
        eventId=str(uuid.uuid4()),
        occurredAt=_now_iso(),
        answerId=answer_id,
        sessionId=session_id,
        questionId=question_id,
        error=error,
        detail=detail,
    )
    await _producer.send_and_wait(
        config.KAFKA_TOPIC_EVALUATION_FAILED,
        value=event.model_dump(),
        key=answer_id,
    )
    logger.info("Published evaluation-failed: answer=%s reason=%s", answer_id, error)
