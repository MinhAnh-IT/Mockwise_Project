from typing import TypedDict, Optional, Any


class OverallReviewState(TypedDict):
    """LangGraph state for the cross-question overall reviewer.

    Mirrors evaluator.state.AgentState shape so the retry / output-validator
    pattern carries over.
    """
    raw_input: dict
    validated_input: Optional[Any]
    retry_count: int
    needs_retry: bool
    evaluation_error: Optional[str]
    last_prompt: Optional[str]
    raw_output: Optional[Any]
    final_output: Optional[dict]
    evaluation_start_ms: Optional[int]
