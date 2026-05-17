"""Evaluator input schemas accepted by {@code POST /evaluate} and the
{@code evaluation-requested} Kafka payload's {@code payload} subfield.
All extend {@link CamelModel} so JSON keys are camelCase on the wire,
matching the orchestrator's outgoing shape.
"""
from typing import Literal, List

from pydantic import Field, field_validator

from models._base import CamelModel


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
    time_limit_minutes: int = 30
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


class BehavioralAnswer(CamelModel):
    transcript: str
    duration_seconds: int
    language: str = "en"


class BehavioralInput(CamelModel):
    session_id: str
    interview_type: Literal["behavioral"]
    question: BehavioralQuestion
    answer: BehavioralAnswer
    response_language: Literal["en", "vi"] = "vi"


# ─── Conceptual Input ─────────────────────────────────────────────────────────

class ConceptualQuestion(CamelModel):
    id: str
    text: str
    domain: str
    key_concepts: List[str]
    depth_expected: str


class ConceptualAnswer(CamelModel):
    transcript: str
    duration_seconds: int
    language: str = "en"


class ConceptualInput(CamelModel):
    session_id: str
    interview_type: Literal["core_conceptual"]
    question: ConceptualQuestion
    answer: ConceptualAnswer
    response_language: Literal["en", "vi"] = "vi"
