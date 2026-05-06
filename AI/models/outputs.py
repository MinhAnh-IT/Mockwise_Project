"""Evaluator output schemas — three discriminated shapes (live coding,
behavioral, conceptual). All extend {@link CamelModel} so JSON keys go on
the wire as camelCase ({@code overallScore}, {@code signalCoverage},
{@code redFlags}, ...) — matching the rest of the Mockwise stack.
Pydantic field names stay snake_case for Python ergonomics.
"""
from typing import Literal, List, Optional

from pydantic import Field

from models._base import CamelModel
from models.common import (
    CompletenessField,
    MetaBlock,
    QualityField,
    ScoreItem,
    SummaryBlock,
)


# ─── Live Coding Output ───────────────────────────────────────────────────────

class LiveCodingScores(CamelModel):
    time_complexity: ScoreItem
    space_complexity: ScoreItem
    code_quality: ScoreItem
    problem_solving: ScoreItem


class ComplexityInfo(CamelModel):
    time: str
    space: str


class CodeIssue(CamelModel):
    type: str
    line: Optional[int] = None
    detail: str


class LiveCodingAnalysis(CamelModel):
    detected_complexity: ComplexityInfo
    optimal_complexity: ComplexityInfo
    is_optimal: bool
    code_issues: List[CodeIssue]


class LiveCodingFeedback(CamelModel):
    strengths: List[str]
    improvements: List[str]
    optimization_hint: str
    sample_optimal_solution: Optional[str] = None


class LiveCodingOutput(CamelModel):
    session_id: str
    interview_type: Literal["live_coding"]
    overall_score: int = Field(..., ge=0, le=100)
    completeness: CompletenessField
    scores: LiveCodingScores
    analysis: LiveCodingAnalysis
    feedback: LiveCodingFeedback
    meta: MetaBlock
    summary: SummaryBlock


# ─── Behavioral Output ────────────────────────────────────────────────────────

class BehavioralScores(CamelModel):
    star_structure: ScoreItem
    relevance: ScoreItem
    specificity: ScoreItem
    impact_result: ScoreItem
    self_awareness: ScoreItem


class StarComponent(CamelModel):
    detected: bool
    quality: QualityField
    excerpt: Optional[str] = None


class StarBreakdown(CamelModel):
    situation: StarComponent
    task: StarComponent
    action: StarComponent
    result: StarComponent


class SignalItem(CamelModel):
    signal_name: str
    detected: bool
    evidence: Optional[str] = None


class RedFlag(CamelModel):
    type: str
    severity: Literal["low", "medium", "high"]
    detail: str


class BehavioralFeedback(CamelModel):
    strengths: List[str]
    improvements: List[str]
    sample_stronger_answer_structure: str


class BehavioralOutput(CamelModel):
    session_id: str
    interview_type: Literal["behavioral"]
    overall_score: int = Field(..., ge=0, le=100)
    completeness: CompletenessField
    scores: BehavioralScores
    star_breakdown: StarBreakdown
    signal_coverage: List[SignalItem]
    red_flags: List[RedFlag]
    feedback: BehavioralFeedback
    meta: MetaBlock
    summary: SummaryBlock


# ─── Conceptual Output ────────────────────────────────────────────────────────

class ConceptualScores(CamelModel):
    accuracy: ScoreItem
    depth: ScoreItem
    practical_application: ScoreItem
    clarity: ScoreItem


class ConceptItem(CamelModel):
    concept_name: str
    mentioned: bool
    correct: Optional[bool] = None
    candidate_statement: Optional[str] = None
    correction: Optional[str] = None


class LevelCalibration(CamelModel):
    expected_level: str
    actual_demonstrated_level: str
    gap: str


class Misconception(CamelModel):
    claim: str
    correction: str


class ConceptualFeedback(CamelModel):
    strengths: List[str]
    improvements: List[str]
    key_points_to_study: List[str]


class ConceptualOutput(CamelModel):
    session_id: str
    interview_type: Literal["core_conceptual"]
    overall_score: int = Field(..., ge=0, le=100)
    completeness: CompletenessField
    scores: ConceptualScores
    concept_coverage: List[ConceptItem]
    level_calibration: LevelCalibration
    misconceptions: List[Misconception]
    feedback: ConceptualFeedback
    meta: MetaBlock
    summary: SummaryBlock
