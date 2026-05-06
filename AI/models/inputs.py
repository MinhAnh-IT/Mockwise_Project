"""Evaluator input schemas accepted by {@code POST /evaluate} and the
{@code evaluation-requested} Kafka payload's {@code payload} subfield.
All extend {@link CamelModel} so JSON keys are camelCase on the wire,
matching the orchestrator's outgoing shape.
"""
from typing import Literal, List

from models._base import CamelModel


# ─── Live Coding Input ────────────────────────────────────────────────────────

class OptimalComplexity(CamelModel):
    time: str
    space: str


class LiveCodingQuestion(CamelModel):
    id: str
    title: str
    description: str
    difficulty: str
    tags: List[str]
    time_limit_minutes: int
    optimal_complexity: OptimalComplexity


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
