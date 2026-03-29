from typing import TypedDict, Optional, Any


class AgentState(TypedDict):
    raw_input: dict
    interview_type: Optional[str]
    validated_input: Optional[Any]
    retry_count: int
    needs_retry: bool
    evaluation_error: Optional[str]
    last_prompt: Optional[str]
    raw_output: Optional[Any]
    final_output: Optional[dict]
    evaluation_start_ms: Optional[int]
