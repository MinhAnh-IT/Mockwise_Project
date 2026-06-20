import time

import config
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

    # Upper-bound the batch size. The generator emits all N cases in a single
    # structured call, whose quality degrades (count drift, duplicate inputs,
    # fake "large" hidden cases) as N grows — reject past the cap rather than
    # ship garbage. Phase B's programmatic input generator can raise this.
    if validated.num_testcases > config.MAX_TESTCASES:
        return {
            "final_output": {
                "error": "too_many_testcases",
                "detail": (
                    f"numTestcases={validated.num_testcases} exceeds the maximum "
                    f"of {config.MAX_TESTCASES}. Request {config.MAX_TESTCASES} or "
                    "fewer — large hidden inputs are better added via the "
                    "programmatic generator than hand-authored by the model."
                ),
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
