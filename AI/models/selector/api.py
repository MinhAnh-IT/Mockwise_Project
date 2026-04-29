from typing import List, Literal, Optional, Any, Dict
from pydantic import BaseModel, Field

from models.selector.snapshot import QuestionSnapshot, QuestionType


# ─── /next-question request ───────────────────────────────────────────────────

class NextQuestionConstraints(BaseModel):
    target_role: Optional[str] = None  # BACKEND/FRONTEND/...
    difficulty_hint: Optional[str] = None  # EASY/MEDIUM/HARD
    competency: Optional[str] = None  # behavioral only
    domain: Optional[str] = None  # core_conceptual only


class NextQuestionRequest(BaseModel):
    session_id: str
    interview_type: QuestionType
    # The full output from the evaluation graph. None for the first turn.
    previous_evaluation: Optional[Dict[str, Any]] = None
    asked_question_ids: List[str] = Field(default_factory=list)
    constraints: NextQuestionConstraints = Field(default_factory=NextQuestionConstraints)


# ─── /next-question response ──────────────────────────────────────────────────

class RetrievalCandidate(BaseModel):
    question_id: str
    similarity: float
    competency: Optional[str] = None
    domain: Optional[str] = None
    difficulty: Optional[str] = None


class RetrievalMeta(BaseModel):
    strategy: str  # "exploit_weakness" | "first_turn" | "explore"
    candidates_considered: int
    top_candidates: List[RetrievalCandidate] = Field(default_factory=list)


class NextQuestionResponse(BaseModel):
    session_id: str
    question_id: str
    question_snapshot: QuestionSnapshot
    rationale: str
    retrieval_meta: RetrievalMeta


# ─── Reindex ──────────────────────────────────────────────────────────────────

class ReindexResponse(BaseModel):
    behavioral_indexed: int
    core_indexed: int
    failed: int = 0
    duration_ms: int
