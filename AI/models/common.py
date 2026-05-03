from enum import Enum
from typing import Annotated
from pydantic import BaseModel, Field, BeforeValidator


class Grade(str, Enum):
    A = "A"
    B = "B"
    C = "C"
    D = "D"
    F = "F"


class HireSignal(str, Enum):
    strong_yes = "strong_yes"
    yes = "yes"
    weak_yes = "weak_yes"
    no = "no"
    strong_no = "strong_no"


class Quality(str, Enum):
    excellent = "excellent"
    good = "good"
    acceptable = "acceptable"
    weak = "weak"
    missing = "missing"


class Completeness(str, Enum):
    """How fully the candidate engaged with the question.

    NO_ANSWER  — silence, "I don't know", or transcript too short to evaluate.
                 interview-service treats this as honest opt-out (UNKNOWN topic
                 status) rather than a wrong answer.
    INCOMPLETE — engaged but missed material expected content; the orchestrator
                 should consider a follow-up to probe the gap.
    COMPLETE   — covered what the question asked for; topic can be closed
                 unless score itself is weak.
    """
    no_answer = "NO_ANSWER"
    incomplete = "INCOMPLETE"
    complete = "COMPLETE"


# Coerce plain strings → Enum instances (needed when Instructor parses Gemini responses)
GradeField = Annotated[Grade, BeforeValidator(lambda v: Grade(v) if isinstance(v, str) else v)]
HireSignalField = Annotated[HireSignal, BeforeValidator(lambda v: HireSignal(v) if isinstance(v, str) else v)]
QualityField = Annotated[Quality, BeforeValidator(lambda v: Quality(v) if isinstance(v, str) else v)]
CompletenessField = Annotated[Completeness, BeforeValidator(lambda v: Completeness(v) if isinstance(v, str) else v)]


class ScoreItem(BaseModel):
    score: int = Field(..., ge=0, le=100)
    max: int = Field(default=100)
    weight: float
    note: str


class MetaBlock(BaseModel):
    evaluated_at: str
    model_version: str
    evaluation_duration_ms: int


class SummaryBlock(BaseModel):
    grade: GradeField
    hire_signal: HireSignalField
    one_line_verdict: str
