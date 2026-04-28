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


# Coerce plain strings → Enum instances (needed when Instructor parses Gemini responses)
GradeField = Annotated[Grade, BeforeValidator(lambda v: Grade(v) if isinstance(v, str) else v)]
HireSignalField = Annotated[HireSignal, BeforeValidator(lambda v: HireSignal(v) if isinstance(v, str) else v)]
QualityField = Annotated[Quality, BeforeValidator(lambda v: Quality(v) if isinstance(v, str) else v)]


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
