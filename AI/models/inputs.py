"""Evaluator input schemas accepted by {@code POST /evaluate} and the
{@code evaluation-requested} Kafka payload's {@code payload} subfield.
All extend {@link CamelModel} so JSON keys are camelCase on the wire,
matching the orchestrator's outgoing shape.
"""
from typing import Dict, Literal, List, Optional

from pydantic import Field, field_validator

from models._base import CamelModel


# ─── Shared evaluation context ────────────────────────────────────────────────

class EvalContext(CamelModel):
    """Situational context the grader needs to set the right bar and understand
    a question. Every field optional so events emitted before the orchestrator
    started sending this block still parse (backward compatibility)."""
    is_follow_up: bool = False
    parent_question_text: Optional[str] = None
    parent_answer_excerpt: Optional[str] = None
    probing_gap: Optional[str] = None


# ─── Live Coding Input ────────────────────────────────────────────────────────

class OptimalComplexity(CamelModel):
    # Defense-in-depth: a question with empty optimal-complexity columns must
    # not hard-fail input validation (that turned every such answer into
    # evaluation-failed → FAILED). Missing/None/blank → a harmless placeholder;
    # the prompt just shows "unknown" instead of a Big-O hint.
    time: str = "unknown"
    space: str = "unknown"

    @field_validator("time", "space", mode="before")
    @classmethod
    def _blank_to_unknown(cls, v):
        return v if v not in (None, "") else "unknown"


class LiveCodingQuestion(CamelModel):
    id: str
    title: str = "Coding Problem"
    description: str = ""
    difficulty: str = "MEDIUM"
    tags: List[str] = Field(default_factory=list)
    # LeetCode-style constraints block (markdown, multi-line). "" when the
    # question setter didn't provide one. There is no per-question time
    # limit any more — the interview is bounded by the session clock.
    constraints: str = ""
    optimal_complexity: OptimalComplexity = Field(default_factory=OptimalComplexity)

    @field_validator("title", "description", "difficulty", mode="before")
    @classmethod
    def _none_to_default(cls, v, info):
        if v is not None and v != "":
            return v
        return {
            "title": "Coding Problem",
            "description": "",
            "difficulty": "MEDIUM",
        }[info.field_name]


class TestSummary(CamelModel):
    total: int
    passed: int
    # Counts of the FAILING cases by judge verdict (e.g. {"WA": 2, "TLE": 1}).
    # Empty when all passed or for events emitted before this was added.
    failed_by_status: Dict[str, int] = Field(default_factory=dict)


class LiveCodingSubmission(CamelModel):
    code: str
    language: str
    time_spent_minutes: int
    test_summary: TestSummary


class LiveCodingInput(CamelModel):
    session_id: str
    interview_type: Literal["live_coding"]
    question: LiveCodingQuestion
    submission: LiveCodingSubmission
    response_language: Literal["en", "vi"] = "vi"


# ─── Behavioral Input ─────────────────────────────────────────────────────────

class BehavioralQuestion(CamelModel):
    id: str
    text: str
    competency: str
    expected_signals: List[str]
    difficulty: Optional[str] = None
    expected_points: Optional[List[str]] = None


class BehavioralAnswer(CamelModel):
    transcript: str
    duration_seconds: int
    language: str = "en"


class BehavioralInput(CamelModel):
    session_id: str
    interview_type: Literal["behavioral"]
    question: BehavioralQuestion
    answer: BehavioralAnswer
    context: Optional[EvalContext] = None
    level: Optional[str] = None
    target_role: Optional[str] = None
    response_language: Literal["en", "vi"] = "vi"


# ─── Conceptual Input ─────────────────────────────────────────────────────────

class ConceptualQuestion(CamelModel):
    id: str
    text: str
    domain: str
    key_concepts: List[str]
    depth_expected: str
    difficulty: Optional[str] = None
    expected_points: Optional[List[str]] = None


class ConceptualAnswer(CamelModel):
    transcript: str
    duration_seconds: int
    language: str = "en"


class ConceptualInput(CamelModel):
    session_id: str
    interview_type: Literal["core_conceptual"]
    question: ConceptualQuestion
    answer: ConceptualAnswer
    context: Optional[EvalContext] = None
    level: Optional[str] = None
    target_role: Optional[str] = None
    response_language: Literal["en", "vi"] = "vi"
