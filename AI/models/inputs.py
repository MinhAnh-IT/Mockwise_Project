from typing import Literal, List
from pydantic import BaseModel


# ─── Live Coding Input ────────────────────────────────────────────────────────

class OptimalComplexity(BaseModel):
    time: str
    space: str


class LiveCodingQuestion(BaseModel):
    id: str
    title: str
    description: str
    difficulty: str
    tags: List[str]
    time_limit_minutes: int
    optimal_complexity: OptimalComplexity


class TestSummary(BaseModel):
    total: int
    passed: int


class LiveCodingSubmission(BaseModel):
    code: str
    language: str
    time_spent_minutes: int
    test_summary: TestSummary


class LiveCodingInput(BaseModel):
    session_id: str
    interview_type: Literal["live_coding"]
    question: LiveCodingQuestion
    submission: LiveCodingSubmission
    response_language: Literal["en", "vi"] = "vi"


# ─── Behavioral Input ─────────────────────────────────────────────────────────

class BehavioralQuestion(BaseModel):
    id: str
    text: str
    competency: str
    expected_signals: List[str]


class BehavioralAnswer(BaseModel):
    transcript: str
    duration_seconds: int
    language: str = "en"


class BehavioralInput(BaseModel):
    session_id: str
    interview_type: Literal["behavioral"]
    question: BehavioralQuestion
    answer: BehavioralAnswer
    response_language: Literal["en", "vi"] = "vi"


# ─── Conceptual Input ─────────────────────────────────────────────────────────

class ConceptualQuestion(BaseModel):
    id: str
    text: str
    domain: str
    key_concepts: List[str]
    depth_expected: str


class ConceptualAnswer(BaseModel):
    transcript: str
    duration_seconds: int
    language: str = "en"


class ConceptualInput(BaseModel):
    session_id: str
    interview_type: Literal["core_conceptual"]
    question: ConceptualQuestion
    answer: ConceptualAnswer
    response_language: Literal["en", "vi"] = "vi"
