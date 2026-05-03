"""Kafka consumer for evaluation-requested.

Pattern mirrors selector/messaging/consumer.py — one long-lived task
started during the FastAPI lifespan, manual offset commits, broad
exception handling so a single bad message does not kill the loop.

Each request:
  1. JSON decode + Pydantic validate.
  2. Run the evaluation graph synchronously inside an executor (the graph
     is sync code that calls Gemini — putting it on the event loop directly
     would block all other coroutines including the producer).
  3. Publish evaluation-completed (or evaluation-failed on hard error).
  4. Commit the offset only after publish succeeds.
"""
from __future__ import annotations

import asyncio
import json
import logging
from typing import Optional

from aiokafka import AIOKafkaConsumer

import config
from main import evaluate
from messaging import evaluation_producer
from models.evaluation_events import EvaluationRequestedEvent

logger = logging.getLogger(__name__)


def _deserialize(raw: bytes):
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception as exc:
        logger.error("Failed to JSON-decode kafka message: %s", exc)
        return None


async def _handle_event(event: EvaluationRequestedEvent) -> None:
    # The graph is sync code. Run it in the default executor so the event
    # loop (and the producer) keep running.
    loop = asyncio.get_running_loop()
    try:
        result_dict = await loop.run_in_executor(None, evaluate, event.payload)
    except Exception as exc:
        logger.exception("Evaluator raised for answer=%s", event.answerId)
        await evaluation_producer.publish_failed(
            answer_id=event.answerId,
            session_id=event.sessionId,
            question_id=event.questionId,
            error="evaluator_exception",
            detail=str(exc),
        )
        return

    # The graph wraps recoverable failures (no output, validation error)
    # in an `error` key on final_output. Surface that as a failed event
    # instead of pretending it succeeded.
    if isinstance(result_dict, dict) and "error" in result_dict:
        await evaluation_producer.publish_failed(
            answer_id=event.answerId,
            session_id=event.sessionId,
            question_id=event.questionId,
            error=str(result_dict.get("error")),
            detail=str(result_dict.get("detail", "")),
        )
        return

    interview_type = (
        result_dict.get("interview_type")
        if isinstance(result_dict, dict)
        else event.payload.get("interview_type", "")
    )
    await evaluation_producer.publish_completed(
        answer_id=event.answerId,
        session_id=event.sessionId,
        question_id=event.questionId,
        interview_type=interview_type or "",
        result=result_dict,
    )


async def run_evaluation_consumer(stop_event: asyncio.Event) -> None:
    if not config.KAFKA_BOOTSTRAP_SERVERS:
        logger.warning("KAFKA_BOOTSTRAP_SERVERS not set; evaluation consumer disabled")
        await stop_event.wait()
        return

    consumer = AIOKafkaConsumer(
        config.KAFKA_TOPIC_EVALUATION_REQUESTED,
        bootstrap_servers=config.KAFKA_BOOTSTRAP_SERVERS,
        group_id=config.KAFKA_EVALUATION_CONSUMER_GROUP,
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        value_deserializer=_deserialize,
    )

    await consumer.start()
    logger.info(
        "Evaluation consumer started: topic=%s group=%s",
        config.KAFKA_TOPIC_EVALUATION_REQUESTED,
        config.KAFKA_EVALUATION_CONSUMER_GROUP,
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
                        event = EvaluationRequestedEvent.model_validate(message.value)
                        await _handle_event(event)
                    except Exception as exc:
                        logger.exception(
                            "Failed to handle evaluation-requested from %s offset=%d: %s",
                            tp, message.offset, exc,
                        )
                        # Continue past the bad message — the offset advances
                        # below. Production should route to a DLQ.
                await consumer.commit({tp: messages[-1].offset + 1})
    finally:
        await consumer.stop()
        logger.info("Evaluation consumer stopped")
