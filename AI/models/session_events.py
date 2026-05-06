"""Kafka envelope events for the session-level overall review pipeline.

Mirrors `services/interview-service/.../message/event/SessionEvaluation*Event.java`.
"""
from __future__ import annotations

from typing import Any, Optional

from models._base import CamelModel


class SessionEvaluationRequestedEvent(CamelModel):
    """Inbound envelope produced by interview-service when a session is
    COMPLETED and every answer has reached a terminal state."""
    eventId: Optional[str] = None
    eventType: str = "SESSION_EVALUATION_REQUESTED"
    occurredAt: Any = None
    sessionId: str
    userId: Optional[str] = None
    targetRole: Optional[str] = None
    level: Optional[str] = None
    interviewType: Optional[str] = None
    blueprint: dict = {}
    answers: list = []
    answerCount: Optional[int] = None


class SessionEvaluationCompletedEvent(CamelModel):
    """Outbound envelope after the overall_reviewer graph runs successfully."""
    eventId: str
    eventType: str = "SESSION_EVALUATION_COMPLETED"
    occurredAt: str
    sessionId: str
    result: dict


class SessionEvaluationFailedEvent(CamelModel):
    """Outbound envelope when the reviewer hit a hard error.
    Orchestrator records it on `metadata.overallReviewError` and leaves
    the session COMPLETED for manual replay."""
    eventId: str
    eventType: str = "SESSION_EVALUATION_FAILED"
    occurredAt: str
    sessionId: str
    error: str
    detail: Optional[str] = None
