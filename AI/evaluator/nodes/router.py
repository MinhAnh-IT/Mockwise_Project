import time
from typing import Any

from agent.state import AgentState
from models.inputs import LiveCodingInput, BehavioralInput, ConceptualInput


_INPUT_MODELS: dict[str, Any] = {
    "live_coding": LiveCodingInput,
    "behavioral": BehavioralInput,
    "core_conceptual": ConceptualInput,
}


def router_node(state: AgentState) -> dict:
    """
    Inspect raw_input, detect interview_type, validate with the appropriate
    Pydantic model, and initialise the per-run counters.
    """
    raw: dict = state["raw_input"]
    interview_type: str = raw.get("interview_type", "")

    if interview_type not in _INPUT_MODELS:
        error_msg = (
            f"Unknown interview_type '{interview_type}'. "
            f"Must be one of: {list(_INPUT_MODELS.keys())}"
        )
        return {
            "interview_type": interview_type or None,
            "evaluation_error": error_msg,
            "final_output": {
                "error": "invalid_interview_type",
                "detail": error_msg,
                "session_id": raw.get("session_id", "unknown"),
            },
        }

    model_cls = _INPUT_MODELS[interview_type]
    try:
        validated = model_cls.model_validate(raw)
    except Exception as exc:
        error_msg = f"Input validation failed: {exc}"
        return {
            "interview_type": interview_type,
            "evaluation_error": error_msg,
            "final_output": {
                "error": "input_validation_failed",
                "detail": error_msg,
                "session_id": raw.get("session_id", "unknown"),
            },
        }

    return {
        "interview_type": interview_type,
        "validated_input": validated,
        "retry_count": 0,
        "needs_retry": False,
        "evaluation_error": None,
        "final_output": None,
        "evaluation_start_ms": int(time.time() * 1000),
    }
