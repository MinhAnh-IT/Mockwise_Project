"""Pydantic schemas for the session-level overall review.

The interview orchestrator publishes a `SessionEvaluationPayload` once every
per-answer evaluation is terminal; the AI overall_reviewer graph consumes
it and produces an `OverallReviewOutput` that the orchestrator stores on
`interview_session.metadata.overallReview`.
"""
from __future__ import annotations

from typing import List, Optional

from pydantic import Field

from models._base import CamelModel
from models.common import Grade, HireSignal


class SessionAnswerSummary(CamelModel):
    """One pinned-question + answer pair the orchestrator hands to the
    overall reviewer. Mirrors what `SessionFinalizerService.buildPayload`
    on the Java side emits — every field optional so a degraded payload
    (e.g. answer FAILED with empty verdict) still parses."""
    session_question_id: str
    sequence: int
    question_type: str  # BEHAVIORAL | CORE_CONCEPTUAL | LIVE_CODING
    topic_kind: Optional[str] = None
    topic_value: Optional[str] = None
    difficulty: Optional[str] = None
    is_follow_up: bool = False
    text: Optional[str] = None
    expected_signals: Optional[List[str]] = None
    key_concepts: Optional[List[str]] = None
    depth_expected: Optional[str] = None
    transcript: Optional[str] = None
    code: Optional[str] = None
    language: Optional[str] = None
    per_answer_score: Optional[float] = None
    per_answer_verdict: dict = Field(default_factory=dict)
    answer_status: str = "MISSING"


class SessionBlueprintSummary(CamelModel):
    """Lightweight blueprint snapshot — the reviewer reads `topics` to label
    coverage gaps and `questionBudget` / `timeBudgetMinutes` for context."""
    id: Optional[str] = None
    question_budget: Optional[int] = None
    time_budget_minutes: Optional[int] = None
    topics: List[dict] = Field(default_factory=list)


class SessionEvaluationPayload(CamelModel):
    """Top-level payload arriving on `session-evaluation-requested`."""
    event_id: Optional[str] = None
    event_type: Optional[str] = None
    occurred_at: Optional[str] = None
    session_id: str
    user_id: Optional[str] = None
    target_role: Optional[str] = None
    level: Optional[str] = None
    interview_type: str
    blueprint: SessionBlueprintSummary = Field(default_factory=SessionBlueprintSummary)
    answers: List[SessionAnswerSummary] = Field(default_factory=list)
    answer_count: Optional[int] = None


class TopicReviewItem(CamelModel):
    """Per-topic verdict assembled from every answer that touched the topic.
    Status mirrors the topic-state lifecycle interview-service tracks
    (NOT_TESTED → PROBING → STRONG | ADEQUATE | PARTIAL | WEAK | UNKNOWN)
    so the orchestrator can update its own state if needed."""
    topic_kind: str
    topic_value: str
    status: str
    comment: str


class OverallReviewOutput(CamelModel):
    """Cross-question review the AI returns for a finished session.

    `overall_score` is bounded 0–10 to match the per-answer scale already
    used on `Answer.score` and the planner thresholds. `grade` and
    `hire_signal` are deterministic projections of the score band — the
    output_validator node enforces consistency.
    """
    session_id: str
    overall_score: float = Field(..., ge=0.0, le=10.0)
    grade: Grade
    hire_signal: HireSignal
    summary: str
    strengths: List[str] = Field(default_factory=list)
    weaknesses: List[str] = Field(default_factory=list)
    per_topic_summary: List[TopicReviewItem] = Field(default_factory=list)
    recommendations: List[str] = Field(default_factory=list)
