from typing import List, Literal, Optional
from pydantic import BaseModel, Field


EventType = Literal[
    "QUESTION_ACTIVATED",
    "QUESTION_UPDATED",
    "QUESTION_DEACTIVATED",
]


class EventSnapshot(BaseModel):
    """Snapshot field of QuestionBankEvent (mirrors Java DTO)."""
    text: Optional[str] = None
    difficulty: Optional[str] = None
    tags: List[str] = Field(default_factory=list)

    # Behavioral
    competency: Optional[str] = None
    expectedSignals: List[str] = Field(default_factory=list)

    # Core
    domain: Optional[str] = None
    targetRoles: List[str] = Field(default_factory=list)
    keyConcepts: List[str] = Field(default_factory=list)
    depthExpected: Optional[str] = None


class QuestionBankEvent(BaseModel):
    """Wire format of events on topic question-bank-events (camelCase from Java side)."""
    eventId: str
    eventType: EventType
    occurredAt: str  # ISO-8601 string from Java OffsetDateTime
    questionId: str
    questionType: Literal["BEHAVIORAL", "CORE_CONCEPTUAL", "LIVE_CODING"]
    snapshot: Optional[EventSnapshot] = None
