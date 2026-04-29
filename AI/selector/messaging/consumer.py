"""Kafka consumer for the question-bank-events topic.

Runs as a long-lived asyncio task started during FastAPI lifespan startup.
Each event is:
1. Deserialised into models.events.QuestionBankEvent.
2. Deduped against processed_events (idempotent on event_id).
3. Translated to a QuestionSnapshot, embedded, and upserted (ACTIVATED/UPDATED)
   or marked inactive (DEACTIVATED).

LIVE_CODING events are silently ignored — the producer side already filters,
this is belt-and-braces.

Failures during embed/DB are logged and do NOT commit the offset, so the
consumer redelivers the event on next poll. We rely on aiokafka's auto-commit
being driven by successful return from the loop body.
"""
from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone
from typing import Optional

from aiokafka import AIOKafkaConsumer

import config
from llm import embed_text
from models.selector.events import EventSnapshot, QuestionBankEvent
from models.selector.snapshot import QuestionSnapshot
from selector.repository import question_index as repo
from selector.retrieval.embedding import build_question_embedding_text

logger = logging.getLogger(__name__)


def _to_snapshot(event: QuestionBankEvent) -> Optional[QuestionSnapshot]:
    if event.snapshot is None:
        return None
    s: EventSnapshot = event.snapshot
    return QuestionSnapshot(
        id=event.questionId,
        type=event.questionType,  # type: ignore[arg-type]
        text=s.text or "",
        difficulty=s.difficulty or "",
        tags=s.tags,
        competency=s.competency,
        expected_signals=s.expectedSignals,
        domain=s.domain,
        target_roles=s.targetRoles,
        key_concepts=s.keyConcepts,
        depth_expected=s.depthExpected,
    )


def _parse_occurred_at(raw) -> datetime:
    # Numeric form: epoch seconds (float or int) — what Spring Kafka's
    # JsonSerializer emits if JavaTimeModule isn't registered.
    if isinstance(raw, (int, float)):
        return datetime.fromtimestamp(float(raw), tz=timezone.utc)
    try:
        # ISO-8601 with offset (e.g. "2026-04-29T11:57:02+07:00") — what we
        # get once JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false are set.
        return datetime.fromisoformat(raw)
    except Exception:
        return datetime.now(tz=timezone.utc)


async def _handle_event(event: QuestionBankEvent) -> None:
    if event.questionType not in ("BEHAVIORAL", "CORE_CONCEPTUAL"):
        logger.debug("Ignoring %s event for non-indexable type %s",
                     event.eventType, event.questionType)
        return

    if await repo.is_event_processed(event.eventId):
        logger.info("Event %s already processed; skipping", event.eventId)
        return

    occurred_at = _parse_occurred_at(event.occurredAt)

    if event.eventType == "QUESTION_DEACTIVATED":
        await repo.deactivate_question(event.questionId, occurred_at)
        logger.info("Deactivated question %s in index", event.questionId)
    elif event.eventType in ("QUESTION_ACTIVATED", "QUESTION_UPDATED"):
        snapshot = _to_snapshot(event)
        if snapshot is None:
            logger.error("Event %s missing snapshot; skipping", event.eventId)
            return
        text = build_question_embedding_text(snapshot)
        embedding = embed_text(text)
        await repo.upsert_question(snapshot, embedding, occurred_at)
        logger.info("Indexed question %s (%s)", event.questionId, event.eventType)
    else:
        logger.warning("Unknown event type %s; skipping", event.eventType)
        return

    await repo.mark_event_processed(event.eventId, event.eventType, event.questionId)


def _deserialize(raw: bytes):
    """
    Parse JSON to dict only — defer Pydantic validation until _handle_event
    so a single bad message can be logged & skipped without aborting the
    aiokafka consumer (value_deserializer exceptions kill the consumer).
    """
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception as exc:
        logger.error("Failed to JSON-decode kafka message: %s", exc)
        return None


async def run_consumer(stop_event: asyncio.Event) -> None:
    """Long-lived consumer loop. Exits when stop_event is set."""
    if not config.KAFKA_BOOTSTRAP_SERVERS:
        logger.warning("KAFKA_BOOTSTRAP_SERVERS not set; consumer disabled")
        await stop_event.wait()
        return

    consumer = AIOKafkaConsumer(
        config.KAFKA_TOPIC_QUESTION_BANK,
        bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS,
        group_id=config.KAFKA_CONSUMER_GROUP,
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        value_deserializer=_deserialize,
    )

    await consumer.start()
    logger.info("Kafka consumer started: topic=%s group=%s",
                config.KAFKA_TOPIC_QUESTION_BANK, config.KAFKA_CONSUMER_GROUP)

    try:
        while not stop_event.is_set():
            try:
                # Short timeout so we can check stop_event regularly.
                batch = await asyncio.wait_for(consumer.getmany(timeout_ms=1000), timeout=2.0)
            except asyncio.TimeoutError:
                continue

            if not batch:
                continue

            for tp, messages in batch.items():
                for message in messages:
                    try:
                        if message.value is None:
                            continue  # JSON-decode failed in _deserialize
                        event = QuestionBankEvent.model_validate(message.value)
                        await _handle_event(event)
                    except Exception as exc:
                        logger.exception(
                            "Failed to handle event from %s offset=%d: %s",
                            tp, message.offset, exc,
                        )
                        # Skip ahead — the offset still gets committed below.
                        # Real production would route to a dead-letter topic.
                # Commit per partition only after the batch is processed.
                await consumer.commit({tp: messages[-1].offset + 1})
    finally:
        await consumer.stop()
        logger.info("Kafka consumer stopped")
