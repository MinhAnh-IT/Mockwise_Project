"""Event payloads on the evaluation-requested / evaluation-completed topics.

Schema mirrors services/interview-service/docs/interview-service-design.md §6.
The orchestrator (interview-service) is the only producer of
`evaluation-requested` and the only consumer of `evaluation-completed`.
"""
from __future__ import annotations

from typing import Any, Optional

from pydantic import Field

from models._base import CamelModel


class EvaluationRequestedEvent(CamelModel):
    """Fired by interview-service once an answer is READY (transcript on file
    for behavioral/core, judge result on file for coding) and the orchestrator
    has assembled the full evaluation payload.

    `payload` is whatever the /evaluate endpoint accepts — i.e. one of
    LiveCodingInput / BehavioralInput / ConceptualInput. The model_dump
    here keeps the schema deliberately loose so the orchestrator can extend
    fields without forcing a coordinated AI-service deploy.
    """
    eventId: str
    eventType: str = "EVALUATION_REQUESTED"
    occurredAt: Any  # epoch float OR ISO string — _parse_occurred_at handles both
    answerId: str
    sessionId: str
    questionId: str
    payload: dict = Field(
        ..., description="Full /evaluate-compatible body for the answer"
    )


class EvaluationCompletedEvent(CamelModel):
    """Fired after the evaluator graph finishes successfully.

    `result` is the final_output dict from the graph — see the per-type
    Output schemas in models/outputs.py.
    """
    eventId: str
    eventType: str = "EVALUATION_COMPLETED"
    occurredAt: str  # ISO-8601 (UTC, "Z" suffix) — produced by us
    answerId: str
    sessionId: str
    questionId: str
    interviewType: str = Field(
        ..., description="behavioral | core_conceptual | live_coding"
    )
    result: dict


class EvaluationFailedEvent(CamelModel):
    """Fired when the evaluator graph exhausted retries or hit an
    unrecoverable error. Interview-service flips the answer to FAILED."""
    eventId: str
    eventType: str = "EVALUATION_FAILED"
    occurredAt: str
    answerId: str
    sessionId: str
    questionId: str
    error: str
    detail: Optional[str] = None
