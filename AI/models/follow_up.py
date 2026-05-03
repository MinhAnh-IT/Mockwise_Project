"""Schemas for the follow-up generation endpoint.

The orchestrator (interview-service) calls this when it has decided a
follow-up is warranted (Case C of the decision tree) but no pre-authored
question matches the weak target. The endpoint is sync REST, not Kafka,
because the user is waiting on the next question.
"""
from __future__ import annotations

from typing import List, Literal, Optional

from pydantic import BaseModel, Field


class WeakTarget(BaseModel):
    """A specific gap to probe in the follow-up.

    `kind` mirrors the categories the evaluator already exposes:
      - signal      → an expected_signal that came back detected=False (behavioral)
      - concept     → a key_concept with mentioned=False or correct=False (core)
      - misconception → a Misconception.claim from the conceptual evaluator
      - red_flag    → a RedFlag.type from the behavioral evaluator
    """
    kind: Literal["signal", "concept", "misconception", "red_flag"]
    value: str = Field(..., description="Specific subtopic / signal name to probe")
    severity: Literal["low", "medium", "high"] = "high"


class StrongTarget(BaseModel):
    """A skill the candidate already demonstrated. Helps the LLM avoid
    re-asking what the candidate has covered well."""
    kind: Literal["signal", "concept"]
    value: str


class ParentQuestion(BaseModel):
    id: str
    type: Literal["BEHAVIORAL", "CORE_CONCEPTUAL"]
    text: str
    competency: Optional[str] = Field(default=None, description="Required for BEHAVIORAL")
    domain: Optional[str] = Field(default=None, description="Required for CORE_CONCEPTUAL")
    expected_signals: Optional[List[str]] = None
    key_concepts: Optional[List[str]] = None


class FollowUpRequest(BaseModel):
    session_id: str
    parent_question: ParentQuestion
    user_answer_transcript: str
    weak_target: WeakTarget
    strong_targets: List[StrongTarget] = Field(default_factory=list)
    difficulty: Literal["EASY", "MEDIUM", "HARD"] = "MEDIUM"
    language: Literal["vi", "en"] = "vi"


class FollowUpResponse(BaseModel):
    question_text: str = Field(..., description="The follow-up question to ask")
    expected_points: List[str] = Field(
        ..., description="What a satisfactory answer must cover (≤ 5 items)"
    )
    rationale: str = Field(
        ..., description="Why this follow-up addresses the weak_target — for audit/debug"
    )
    source: Literal["AI_GENERATED"] = "AI_GENERATED"
    model_meta: dict = Field(default_factory=dict)
