"""Schemas for the follow-up generation endpoint.

The orchestrator (interview-service) calls this when it has decided a
follow-up is warranted (Case C of the decision tree) but no pre-authored
question matches the weak target. The endpoint is sync REST, not Kafka,
because the user is waiting on the next question.
"""
from __future__ import annotations

from typing import List, Literal, Optional

from pydantic import Field

from models._base import CamelModel


class WeakTarget(CamelModel):
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


class StrongTarget(CamelModel):
    """A skill the candidate already demonstrated. Helps the LLM avoid
    re-asking what the candidate has covered well."""
    kind: Literal["signal", "concept"]
    value: str


class ParentQuestion(CamelModel):
    id: str
    type: Literal["BEHAVIORAL", "CORE_CONCEPTUAL"]
    text: str
    competency: Optional[str] = Field(default=None, description="Required for BEHAVIORAL")
    domain: Optional[str] = Field(default=None, description="Required for CORE_CONCEPTUAL")
    expected_signals: Optional[List[str]] = None
    key_concepts: Optional[List[str]] = None


class FollowUpRequest(CamelModel):
    session_id: str
    parent_question: ParentQuestion
    user_answer_transcript: str
    weak_target: WeakTarget
    strong_targets: List[StrongTarget] = Field(default_factory=list)
    difficulty: Literal["EASY", "MEDIUM", "HARD"] = "MEDIUM"
    language: Literal["vi", "en"] = "vi"


class FollowUpLLMOutput(CamelModel):
    """The content fields the LLM actually produces.

    Kept separate from FollowUpResponse so the structured-output schema sent to
    Gemini contains ONLY fixed-shape fields. A bare `dict` field (model_meta)
    makes Instructor emit `additionalProperties` in the schema, which the Gemini
    Developer API rejects ("additionalProperties is only supported in Gemini
    Enterprise Agent Platform mode") → the call 500s. `source` and `model_meta`
    are stamped server-side after the call anyway, so they never need to be part
    of what the model is asked to generate.
    """
    # min_length guards against Gemini occasionally returning a 200 with an
    # empty question_text (rare, but it freezes the live interview because the
    # orchestrator can't pin a usable next question). An empty value now fails
    # Pydantic validation, which makes Instructor re-prompt and retry instead of
    # handing back a blank.
    question_text: str = Field(
        ..., min_length=1, description="The follow-up question to ask"
    )
    expected_points: List[str] = Field(
        ..., min_length=1, description="What a satisfactory answer must cover (≤ 5 items)"
    )
    rationale: str = Field(
        ..., min_length=1, description="Why this follow-up addresses the weak_target — for audit/debug"
    )


class FollowUpResponse(FollowUpLLMOutput):
    source: Literal["AI_GENERATED"] = "AI_GENERATED"
    model_meta: dict = Field(default_factory=dict)
