import time
from typing import Any

from evaluator.state import AgentState
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

    Accepts both ``interview_type`` (Python convention, used by the
    /evaluate REST examples) and ``interviewType`` (camelCase, what the
    rest of the Mockwise stack emits). The Pydantic models below already
    accept both via ``populate_by_name=True`` — this lookup is the only
    raw-dict access that bypassed that, so it has to handle both keys
    explicitly.
    """
    raw: dict = state["raw_input"]
    interview_type: str = raw.get("interview_type") or raw.get("interviewType") or ""

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
                "session_id": raw.get("session_id") or raw.get("sessionId") or "unknown",
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
                "session_id": raw.get("session_id") or raw.get("sessionId") or "unknown",
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
