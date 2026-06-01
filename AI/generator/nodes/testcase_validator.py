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


# ── Shape buckets (must mirror problem_analyzer._ALLOWED_PARAM_TYPES) ─────────
# Coarse-grained "array vs scalar" classification — the judge's StdinBuilder /
# UniversalDriver serialise these as JSON arrays vs JSON scalars, and the
# OutputComparator compares JsonNode shapes. Mismatch (e.g. scalar `2` where
# the driver will print `[2,2]`) → WA every case.
_ARRAY_SHAPED_TYPES = frozenset({
    "int[]", "long[]", "double[]", "String[]", "string[]",
    "int[][]", "char[][]", "String[][]",
    "List<Integer>", "List<String>",
    "List<List<Integer>>", "List<List<String>>",
    # TreeNode / ListNode are level-order BFS / flat int arrays respectively.
    "TreeNode", "ListNode",
})
_SCALAR_SHAPED_TYPES = frozenset({
    "int", "long", "double", "boolean",
    "Integer", "Long", "Double", "Boolean",
    "char", "Character",
    "String", "string",
})


def _shape_matches(value: Any, type_str: str) -> bool:
    """
    Coarse JSON-shape check: array-typed contracts demand a list, scalar
    contracts demand a non-list/non-dict primitive. We deliberately stop here
    instead of full element-type checking — picking apart e.g. ``int[][]`` vs
    ``char[][]`` would mostly catch typos that StdinBuilder would also surface,
    and over-validation tends to false-positive on numeric ambiguity (Python
    bools are ints, etc.). The narrow goal is to stop the
    ``in_place=true → scalar result`` class of bugs the Remove Element draft
    fell into.
    """
    if type_str in _ARRAY_SHAPED_TYPES:
        return isinstance(value, list)
    if type_str in _SCALAR_SHAPED_TYPES:
        return not isinstance(value, (list, dict)) and value is not None
    # Unknown type — defer to the upstream Pydantic whitelist; here we're
    # lenient so this helper never becomes the single point of failure.
    return True


def _constraints_md(items: Any) -> str:
    """
    Render the analyzer's ``constraints: List[str]`` as a multi-line markdown
    bullet list (LeetCode-style — one constraint per row). Empty / missing →
    "" so the optional column stays null end-to-end.
    """
    if not items:
        return ""
    if isinstance(items, str):
        return items.strip()
    return "\n".join(
        f"- {str(c).strip()}" for c in items if str(c).strip()
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
        constraints=_constraints_md(analysis.get("constraints")),
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
    6. inputData keys == params[].name exactly (StdinBuilder lookup-by-name).
    7. expectedOutput has exactly one key "result" (OutputComparator picks
       the first field of the wrapper).
    8. expectedOutput.result JSON shape matches what the judge driver will
       print: array for in_place=true / array return_type, scalar otherwise.

    On failure: request retry (max GENERATOR_MAX_RETRIES = 3).
    On retry exhaustion: return partial result with warning.
    On success: assemble and return final_output.

    A separate "1b" bail-out path catches malformed function_meta upstream
    (in_place=true with non-void return_type) without spending retries.
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

    # ── 1b. Sanity belt on analysis ──────────────────────────────────────────
    # problem_analyzer.ProblemAnalysisOutput._enforce_in_place_void already
    # rejects this combo at the Pydantic layer, but a stale cached analysis
    # (manual rerun without re-analyzing) could still reach us. Bail out
    # immediately — retrying testcase generation can't fix a malformed
    # function-meta upstream.
    if analysis.get("in_place") and analysis.get("return_type") != "void":
        return {
            "needs_retry": False,
            "final_output": {
                "error": "invalid_function_meta",
                "detail": (
                    f"in_place=true is incompatible with "
                    f"return_type='{analysis.get('return_type')}'. The judge's "
                    "driver discards the return value when in_place is true. "
                    "Re-run the generator end-to-end so problem_analyzer can "
                    "re-derive the function meta (it will reject this combo)."
                ),
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

    # Pre-compute the contract the driver will enforce per testcase so we can
    # tell the LLM EXACTLY why each case is broken on retry. The driver prints
    # params[0] post-mutation when in_place=true and the return value otherwise.
    param_names = {p["name"] for p in analysis.get("params", [])}
    if analysis.get("in_place"):
        # in_place=true ⇒ return_type already enforced to "void" by upstream,
        # so the comparator target is params[0]'s type.
        first_param = analysis["params"][0] if analysis.get("params") else None
        expected_result_type = first_param["type"] if first_param else None
    else:
        expected_result_type = analysis.get("return_type")

    seen_inputs: set[str] = set()
    for i, tc in enumerate(raw_testcases):
        tc_id = tc.get("id", i)
        input_data = tc.get("inputData", {})

        # Canonicalize so key-order differences don't trigger false duplicates
        key = json.dumps(input_data, sort_keys=True, default=str)
        if key in seen_inputs:
            violations.append(
                f"Testcase '{tc_id}' has duplicate inputData: {input_data}."
            )
        else:
            seen_inputs.add(key)

        # inputData keys MUST equal params[].name exactly — judge-service
        # StdinBuilder.build looks up each param by name; an extra/missing key
        # silently feeds `null` into the driver and the candidate's code blows
        # up at parse time with no friendly signal.
        if isinstance(input_data, dict) and param_names:
            input_keys = set(input_data.keys())
            missing = param_names - input_keys
            extra = input_keys - param_names
            if missing or extra:
                violations.append(
                    f"Testcase '{tc_id}' inputData keys mismatch params[].name: "
                    f"missing={sorted(missing) or None}, extra={sorted(extra) or None}. "
                    f"Expected exactly {sorted(param_names)}."
                )

        # expectedOutput must be a JSON object with exactly one key "result"
        # (OutputComparator.compare extracts wrapper.fields().next() — any
        # extra keys silently become hidden state).
        expected = tc.get("expectedOutput")
        if not expected:
            violations.append(
                f"Testcase '{tc_id}' has empty or missing expectedOutput."
            )
            continue
        if not isinstance(expected, dict):
            violations.append(
                f"Testcase '{tc_id}' expectedOutput must be a JSON object, "
                f"got {type(expected).__name__}."
            )
            continue
        if set(expected.keys()) != {"result"}:
            violations.append(
                f"Testcase '{tc_id}' expectedOutput must have exactly one key 'result', "
                f"got keys: {sorted(expected.keys())}."
            )
            continue

        # Coarse shape check: the JSON shape of `result` must match what the
        # driver will print. This is the catch-net for the in_place=true vs
        # scalar-return class of bug.
        if expected_result_type and not _shape_matches(expected["result"], expected_result_type):
            target = (
                f"params[0].type='{expected_result_type}' (in_place=true — driver "
                f"prints the mutated first argument)"
                if analysis.get("in_place")
                else f"return_type='{expected_result_type}'"
            )
            violations.append(
                f"Testcase '{tc_id}' expectedOutput.result shape does not match {target}. "
                f"Got value of type {type(expected['result']).__name__}; expected "
                f"{'a JSON array' if expected_result_type in _ARRAY_SHAPED_TYPES else 'a JSON scalar'}."
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
