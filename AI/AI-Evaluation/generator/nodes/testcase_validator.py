import json
import time
from datetime import datetime, timezone
from typing import Any

import config
from generator.state import GeneratorState
from models.generator_inputs import GenerateTestcasesRequest
from models.generator_outputs import (
    FunctionMeta,
    GeneratedMeta,
    GeneratedTestCase,
    GenerateTestcasesResponse,
    ParamMeta,
    StarterCode,
)


def _build_final_response(
    req: GenerateTestcasesRequest,
    analysis: dict,
    testcases: list,
    generation_start_ms: int,
    warning: str = None,
    leetcode_problem: dict | None = None,
) -> dict:
    """Assemble GenerateTestcasesResponse from analysis + validated testcases."""
    now_ms = int(time.time() * 1000)
    duration_ms = max(0, now_ms - generation_start_ms)

    function_meta = FunctionMeta(
        fn=analysis["fn"],
        params=[ParamMeta(name=p["name"], type=p["type"]) for p in analysis["params"]],
        return_type=analysis["return_type"],
        orderMatters=analysis["order_matters"],
        inPlace=analysis["in_place"],
    )

    sc = analysis["starter_code"]
    starter_code = StarterCode(
        python=sc["python"],
        java=sc["java"],
        cpp=sc["cpp"],
        javascript=sc["javascript"],
    )

    tc_objects = [GeneratedTestCase(**tc) for tc in testcases]
    hidden_count = sum(1 for tc in tc_objects if tc.is_hidden)
    visible_count = len(tc_objects) - hidden_count

    response = GenerateTestcasesResponse(
        title=analysis["title"],
        description=analysis["description"],
        difficulty=analysis["difficulty"],
        tags=analysis["tags"],
        time_limit_minutes=analysis["time_limit_minutes"],
        optimal_time_complexity=analysis["optimal_time_complexity"],
        optimal_space_complexity=analysis["optimal_space_complexity"],
        function_meta=function_meta,
        starter_code=starter_code,
        testcases=tc_objects,
        meta=GeneratedMeta(
            mode=req.mode,
            leetcode_number=(leetcode_problem or {}).get("number"),
            generated_at=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            model_version=config.MODEL_VERSION,
            generation_duration_ms=duration_ms,
            total_testcases=len(tc_objects),
            visible_testcases=visible_count,
            hidden_testcases=hidden_count,
        ),
        warning=warning,
    )

    # The whole response is camelCase via alias_generator; FunctionMeta uses
    # field-level alias to keep "return" (instead of "returnType").
    return response.model_dump(by_alias=True)


def testcase_validator_node(state: GeneratorState) -> dict:
    """
    Validate testcases produced by testcase_generator_node:

    1. raw_testcases must not be None.
    2. Total count == req.num_testcases.
    3. Hidden count == req.num_hidden.
    4. No duplicate inputData.
    5. No testcase with empty expectedOutput.

    On failure: request retry (max GENERATOR_MAX_RETRIES = 3).
    On retry exhaustion: return partial result with warning.
    On success: assemble and return final_output.
    """
    req: GenerateTestcasesRequest = state["validated_input"]
    analysis: dict = state["problem_analysis"]
    raw_testcases: Any = state.get("raw_testcases")
    retry_count: int = state.get("retry_count", 0)
    generation_start_ms: int = state.get("generation_start_ms", int(time.time() * 1000))
    leetcode_problem: dict | None = state.get("leetcode_problem")

    max_retries = config.GENERATOR_MAX_RETRIES

    # ── 1. Check raw_testcases present ───────────────────────────────────────
    if not raw_testcases:
        error = state.get("generation_error") or "Testcase generator produced no output."
        if retry_count < max_retries:
            return {
                "needs_retry": True,
                "retry_count": retry_count + 1,
                "generation_error": f"No testcases produced. {error}. Please generate all {req.num_testcases} testcases.",
            }
        return {
            "needs_retry": False,
            "final_output": {
                "error": "generation_failed",
                "detail": error,
                "retry_attempts": retry_count,
            },
        }

    # ── 2. Collect violations ─────────────────────────────────────────────────
    violations = []

    total = len(raw_testcases)
    if total != req.num_testcases:
        violations.append(
            f"Expected exactly {req.num_testcases} testcases but got {total}."
        )

    hidden_count = sum(1 for tc in raw_testcases if tc.get("is_hidden", False))
    if hidden_count != req.num_hidden:
        violations.append(
            f"Expected exactly {req.num_hidden} hidden testcases (is_hidden=true) but got {hidden_count}."
        )

    seen_inputs: set[str] = set()
    for i, tc in enumerate(raw_testcases):
        input_data = tc.get("inputData", {})
        # Canonicalize so key-order differences don't trigger false duplicates
        key = json.dumps(input_data, sort_keys=True, default=str)
        if key in seen_inputs:
            violations.append(
                f"Testcase '{tc.get('id', i)}' has duplicate inputData: {input_data}."
            )
        else:
            seen_inputs.add(key)

        if not tc.get("expectedOutput"):
            violations.append(
                f"Testcase '{tc.get('id', i)}' has empty or missing expectedOutput."
            )

    # ── 3. Handle violations ──────────────────────────────────────────────────
    if violations:
        error_msg = "Validation failed:\n" + "\n".join(f"  - {v}" for v in violations)

        if retry_count < max_retries:
            return {
                "needs_retry": True,
                "retry_count": retry_count + 1,
                "generation_error": error_msg,
            }

        # Retry exhausted — return best-effort partial result with warning
        return {
            "needs_retry": False,
            "final_output": _build_final_response(
                req, analysis, raw_testcases, generation_start_ms,
                warning=f"max_retries_exceeded ({max_retries}). Some issues remain: {'; '.join(violations)}",
                leetcode_problem=leetcode_problem,
            ),
        }

    # ── 4. All valid — assemble final response ────────────────────────────────
    return {
        "needs_retry": False,
        "final_output": _build_final_response(
            req, analysis, raw_testcases, generation_start_ms,
            leetcode_problem=leetcode_problem,
        ),
    }
