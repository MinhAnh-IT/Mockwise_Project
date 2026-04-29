from typing import List, Literal, Optional
from pydantic import BaseModel, Field


QuestionType = Literal["BEHAVIORAL", "CORE_CONCEPTUAL"]


class QuestionSnapshot(BaseModel):
    """
    Question payload used for indexing and returned alongside /next-question
    responses so the caller does not need to re-fetch from question-bank.
    """
    id: str
    type: QuestionType
    text: str
    difficulty: str
    tags: List[str] = Field(default_factory=list)

    # Behavioral
    competency: Optional[str] = None
    expected_signals: List[str] = Field(default_factory=list)

    # Core conceptual
    domain: Optional[str] = None
    target_roles: List[str] = Field(default_factory=list)
    key_concepts: List[str] = Field(default_factory=list)
    depth_expected: Optional[str] = None
