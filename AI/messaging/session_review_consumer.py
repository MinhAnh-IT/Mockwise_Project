"""Kafka consumer for session-evaluation-requested.

Mirrors `evaluation_consumer.py` — one long-lived task started during the
FastAPI lifespan, manual offset commit only after publish, run the sync
LangGraph in the default executor so the event loop stays unblocked.
"""
from __future__ import annotations

import asyncio
import json
import logging
from typing import Optional

from aiokafka import AIOKafkaConsumer

import config
from messaging import session_review_producer
from models.session_events import SessionEvaluationRequestedEvent
from overall_reviewer.graph import review_session

logger = logging.getLogger(__name__)


def _deserialize(raw: bytes):
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception as exc:
        logger.error("Failed to JSON-decode kafka message: %s", exc)
        return None


async def _handle_event(event_dict: dict) -> None:
    session_id = event_dict.get("sessionId") or event_dict.get("session_id") or ""
    loop = asyncio.get_running_loop()
    try:
        # The full payload is what the overall_reviewer graph expects —
        # input_validator_node parses it through SessionEvaluationPayload.
        result_dict = await loop.run_in_executor(None, review_session, event_dict)
    except Exception as exc:
        logger.exception("Overall reviewer raised for session=%s", session_id)
        await session_review_producer.publish_failed(
            session_id=session_id,
            error="reviewer_exception",
            detail=str(exc),
        )
        return

    if isinstance(result_dict, dict) and "error" in result_dict:
        await session_review_producer.publish_failed(
            session_id=session_id,
            error=str(result_dict.get("error")),
            detail=str(result_dict.get("detail", "")),
        )
        return

    await session_review_producer.publish_completed(
        session_id=session_id,
        result=result_dict,
    )


async def run_session_review_consumer(stop_event: asyncio.Event) -> None:
    if not config.KAFKA_BOOTSTRAP_SERVERS:
        logger.warning("KAFKA_BOOTSTRAP_SERVERS not set; session-review consumer disabled")
        await stop_event.wait()
        return

    consumer = AIOKafkaConsumer(
        config.KAFKA_TOPIC_SESSION_EVAL_REQUESTED,
        bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS,
        group_id=config.KAFKA_SESSION_EVAL_CONSUMER_GROUP,
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        value_deserializer=_deserialize,
    )

    await consumer.start()
    logger.info(
        "Session-review consumer started: topic=%s group=%s",
        config.KAFKA_TOPIC_SESSION_EVAL_REQUESTED,
        config.KAFKA_SESSION_EVAL_CONSUMER_GROUP,
    )

    try:
        while not stop_event.is_set():
            try:
                batch = await asyncio.wait_for(consumer.getmany(timeout_ms=1000), timeout=2.0)
            except asyncio.TimeoutError:
                continue

            if not batch:
                continue

            for tp, messages in batch.items():
                for message in messages:
                    if message.value is None:
                        continue
                    try:
                        # Light Pydantic validation just to surface gross
                        # malformed payloads early — the graph re-validates
                        # via SessionEvaluationPayload internally.
                        SessionEvaluationRequestedEvent.model_validate(message.value)
                        await _handle_event(message.value)
                    except Exception as exc:
                        logger.exception(
                            "Failed to handle session-evaluation-requested from %s offset=%d: %s",
                            tp, message.offset, exc,
                        )
                await consumer.commit({tp: messages[-1].offset + 1})
    finally:
        await consumer.stop()
        logger.info("Session-review consumer stopped")
