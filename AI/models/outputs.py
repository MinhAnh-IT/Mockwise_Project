from typing import Literal, List, Optional
from pydantic import BaseModel, Field

from models.common import ScoreItem, MetaBlock, SummaryBlock, QualityField


# ─── Live Coding Output ───────────────────────────────────────────────────────

class LiveCodingScores(BaseModel):
    time_complexity: ScoreItem
    space_complexity: ScoreItem
    code_quality: ScoreItem
    problem_solving: ScoreItem


class ComplexityInfo(BaseModel):
    time: str
    space: str


class CodeIssue(BaseModel):
    type: str
    line: Optional[int] = None
    detail: str


class LiveCodingAnalysis(BaseModel):
    detected_complexity: ComplexityInfo
    optimal_complexity: ComplexityInfo
    is_optimal: bool
    code_issues: List[CodeIssue]


class LiveCodingFeedback(BaseModel):
    strengths: List[str]
    improvements: List[str]
    optimization_hint: str
    sample_optimal_solution: Optional[str] = None


class LiveCodingOutput(BaseModel):
    session_id: str
    interview_type: Literal["live_coding"]
    overall_score: int = Field(..., ge=0, le=100)
    scores: LiveCodingScores
    analysis: LiveCodingAnalysis
    feedback: LiveCodingFeedback
    meta: MetaBlock
    summary: SummaryBlock


# ─── Behavioral Output ────────────────────────────────────────────────────────

class BehavioralScores(BaseModel):
    star_structure: ScoreItem
    relevance: ScoreItem
    specificity: ScoreItem
    impact_result: ScoreItem
    self_awareness: ScoreItem


class StarComponent(BaseModel):
    detected: bool
    quality: QualityField
    excerpt: Optional[str] = None


class StarBreakdown(BaseModel):
    situation: StarComponent
    task: StarComponent
    action: StarComponent
    result: StarComponent


class SignalItem(BaseModel):
    signal_name: str
    detected: bool
    evidence: Optional[str] = None


class RedFlag(BaseModel):
    type: str
    severity: Literal["low", "medium", "high"]
    detail: str


class BehavioralFeedback(BaseModel):
    strengths: List[str]
    improvements: List[str]
    sample_stronger_answer_structure: str


class BehavioralOutput(BaseModel):
    session_id: str
    interview_type: Literal["behavioral"]
    overall_score: int = Field(..., ge=0, le=100)
    scores: BehavioralScores
    star_breakdown: StarBreakdown
    signal_coverage: List[SignalItem]
    red_flags: List[RedFlag]
    feedback: BehavioralFeedback
    meta: MetaBlock
    summary: SummaryBlock


# ─── Conceptual Output ────────────────────────────────────────────────────────

class ConceptualScores(BaseModel):
    accuracy: ScoreItem
    depth: ScoreItem
    practical_application: ScoreItem
    clarity: ScoreItem


class ConceptItem(BaseModel):
    concept_name: str
    mentioned: bool
    correct: Optional[bool] = None
    candidate_statement: Optional[str] = None
    correction: Optional[str] = None


class LevelCalibration(BaseModel):
    expected_level: str
    actual_demonstrated_level: str
    gap: str


class Misconception(BaseModel):
    claim: str
    correction: str


class ConceptualFeedback(BaseModel):
    strengths: List[str]
    improvements: List[str]
    key_points_to_study: List[str]


class ConceptualOutput(BaseModel):
    session_id: str
    interview_type: Literal["core_conceptual"]
    overall_score: int = Field(..., ge=0, le=100)
    scores: ConceptualScores
    concept_coverage: List[ConceptItem]
    level_calibration: LevelCalibration
    misconceptions: List[Misconception]
    feedback: ConceptualFeedback
    meta: MetaBlock
    summary: SummaryBlock
