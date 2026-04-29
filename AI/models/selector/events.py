from typing import List, Literal, Optional, Union
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
    # Java's OffsetDateTime serialises to ISO-8601 string when JsonSerializer
    # has JavaTimeModule registered. We accept float/int as a fallback in case
    # an older producer (without that config) sends epoch seconds.
    occurredAt: Union[str, float, int]
    questionId: str
    questionType: Literal["BEHAVIORAL", "CORE_CONCEPTUAL", "LIVE_CODING"]
    snapshot: Optional[EventSnapshot] = None
