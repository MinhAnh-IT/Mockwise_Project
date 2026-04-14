import time

from generator.state import GeneratorState
from models.generator_inputs import GenerateTestcasesRequest


def generator_router_node(state: GeneratorState) -> dict:
    """
    Validate the incoming request and initialize run counters.
    On validation failure, set final_output immediately so the graph ends.
    """
    raw: dict = state["raw_input"]

    try:
        validated = GenerateTestcasesRequest.model_validate(raw)
    except Exception as exc:
        return {
            "final_output": {
                "error": "input_validation_failed",
                "detail": str(exc),
            }
        }

    return {
        "validated_input": validated,
        "retry_count": 0,
        "needs_retry": False,
        "generation_error": None,
        "final_output": None,
        "problem_analysis": None,
        "raw_testcases": None,
        "generation_start_ms": int(time.time() * 1000),
    }
