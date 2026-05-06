from typing import List, Optional, Any, Dict

from pydantic import Field

from models._base import CamelModel
from models.selector.snapshot import QuestionSnapshot, QuestionType


# ─── /next-question request ───────────────────────────────────────────────────

class NextQuestionConstraints(CamelModel):
    target_role: Optional[str] = None  # BACKEND/FRONTEND/...
    difficulty_hint: Optional[str] = None  # EASY/MEDIUM/HARD
    competency: Optional[str] = None  # behavioral only
    domain: Optional[str] = None  # core_conceptual only


class NextQuestionRequest(CamelModel):
    session_id: str
    interview_type: QuestionType
    # The full output from the evaluation graph. None for the first turn.
    previous_evaluation: Optional[Dict[str, Any]] = None
    asked_question_ids: List[str] = Field(default_factory=list)
    constraints: NextQuestionConstraints = Field(default_factory=NextQuestionConstraints)


# ─── /next-question response ──────────────────────────────────────────────────

class RetrievalCandidate(CamelModel):
    question_id: str
    similarity: float
    competency: Optional[str] = None
    domain: Optional[str] = None
    difficulty: Optional[str] = None


class RetrievalMeta(CamelModel):
    strategy: str  # "exploit_weakness" | "first_turn" | "explore"
    candidates_considered: int
    top_candidates: List[RetrievalCandidate] = Field(default_factory=list)


class NextQuestionResponse(CamelModel):
    session_id: str
    question_id: str
    question_snapshot: QuestionSnapshot
    rationale: str
    retrieval_meta: RetrievalMeta


# ─── Reindex ──────────────────────────────────────────────────────────────────

class ReindexResponse(CamelModel):
    behavioral_indexed: int
    core_indexed: int
    failed: int = 0
    duration_ms: int
