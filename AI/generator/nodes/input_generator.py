"""
Programmatic input generator (phase B) — large/random HIDDEN-case inputs.

The LLM cannot hand-type a 10^4-element array, so the model's "large hidden"
cases are usually small/fake and fail to separate an O(n) solution from an O(n²)
brute force. This node instead asks the model for a small ``gen_inputs(num_cases,
seed)`` *function*, runs it in a sandboxed subprocess to synthesise genuinely
large, constraint-valid inputs, and swaps them into the hidden testcases. The
existing ``expected_verifier`` then computes their authoritative outputs via the
reference solution — so nothing here trusts an LLM-computed answer.

Safety gates (all must hold, else this node is a no-op and the LLM's hand-typed
hidden cases are kept):
  • PROGRAMMATIC_INPUTS flag on;
  • VERIFY_EXPECTED_OUTPUTS on — we depend on it to fill the swapped outputs;
  • analysis.unique_answer is true — a random input on a "return any valid X"
    problem would let a candidate's equally-correct answer WA against our single
    stored result (the judge does exact match).

Any failure (LLM error, exec crash/timeout, invalid output) degrades gracefully:
the hidden cases are left as the LLM authored them and a note is surfaced.
"""

import json
import subprocess
import sys
import tempfile

from pydantic import BaseModel

import config
from llm import call_structured
from generator.prompts.input_generation import build_input_generation_prompt


class _GenSource(BaseModel):
    source: str   # full Python source defining `def gen_inputs(num_cases, seed)`


# Harness that injects the LLM-authored generator, calls it and prints the cases
# as JSON. `string` is imported for generators that build random text.
_HARNESS = """import json, sys, random, string
{source}
_cases = gen_inputs({num_cases}, {seed})
sys.stdout.write(json.dumps(_cases))
"""

_ARRAY_PLACEHOLDER_TYPES = {
    "int[]", "long[]", "double[]", "String[]", "string[]",
    "int[][]", "char[][]", "String[][]",
    "List<Integer>", "List<String>", "List<List<Integer>>", "List<List<String>>",
    "TreeNode", "ListNode",
}


def _placeholder(type_str: str):
    """A shape-valid dummy `result` so a case still passes the validator's shape
    check if expected_verifier somehow cannot resolve it (it normally will)."""
    if type_str in _ARRAY_PLACEHOLDER_TYPES:
        return []
    if type_str in ("double", "Double"):
        return 0.0
    if type_str in ("boolean", "Boolean"):
        return False
    if type_str in ("String", "string", "char", "Character"):
        return ""
    return 0


def _run_generator(source: str, num_cases: int, seed: int, timeout: int) -> list:
    """Execute the generator in an isolated subprocess; return parsed cases."""
    script = _HARNESS.format(source=source, num_cases=num_cases, seed=seed)
    with tempfile.NamedTemporaryFile("w", suffix=".py", delete=True) as f:
        f.write(script)
        f.flush()
        proc = subprocess.run(
            [sys.executable, f.name],
            capture_output=True, text=True, timeout=timeout,
        )
    if proc.returncode != 0:
        raise RuntimeError(f"generator exited {proc.returncode}: {proc.stderr.strip()[:400]}")
    cases = json.loads(proc.stdout)
    if not isinstance(cases, list):
        raise RuntimeError(f"gen_inputs returned {type(cases).__name__}, expected list")
    return cases


def _valid_inputs(cases: list, param_names: set, seen: set[str]) -> list:
    """Keep only dicts whose keys match params exactly and are not duplicates."""
    kept = []
    for c in cases:
        if not isinstance(c, dict) or set(c.keys()) != param_names:
            continue
        key = json.dumps(c, sort_keys=True, default=str)
        if key in seen:
            continue
        seen.add(key)
        kept.append(c)
    return kept


def input_generator_node(state) -> dict:
    if not getattr(config, "PROGRAMMATIC_INPUTS", False):
        return {}
    if not getattr(config, "VERIFY_EXPECTED_OUTPUTS", True):
        # We rely on expected_verifier to fill the swapped cases' outputs.
        return {"programmatic_warning": "programmatic inputs skipped (expected-output verification is off)"}

    analysis = state.get("problem_analysis")
    raw_testcases = state.get("raw_testcases")
    if not analysis or not raw_testcases:
        return {}

    if not analysis.get("unique_answer", False):
        return {"programmatic_warning": (
            "programmatic large inputs skipped — problem accepts multiple valid "
            "answers (unique_answer=false), so random inputs would mis-grade; "
            "kept the model's hand-crafted hidden cases"
        )}

    hidden_idx = [i for i, tc in enumerate(raw_testcases) if tc.get("is_hidden")]
    if not hidden_idx:
        return {}

    param_names = {p["name"] for p in analysis.get("params", [])}
    if analysis.get("in_place") and analysis.get("params"):
        result_type = analysis["params"][0]["type"]
    else:
        result_type = analysis.get("return_type", "")

    timeout = getattr(config, "EXPECTED_VERIFY_TIMEOUT_SECONDS", 15)
    seed = getattr(config, "PROGRAMMATIC_INPUT_SEED", 42)

    # Inputs already in use by visible (and other) cases — never collide with them.
    seen: set[str] = {
        json.dumps(tc.get("inputData", {}), sort_keys=True, default=str)
        for i, tc in enumerate(raw_testcases) if i not in set(hidden_idx)
    }

    try:
        prompt = build_input_generation_prompt(analysis, len(hidden_idx))
        gen: _GenSource = call_structured(
            prompt, _GenSource, max_tokens=4096,
            model=config.GENERATOR_MODEL_NAME,
            thinking_level=config.GENERATOR_THINKING_LEVEL,
        )
        # Over-generate a little so dedup/validation drop-off still fills the slots.
        cases = _run_generator(gen.source, len(hidden_idx) + 3, seed, timeout)
    except (subprocess.TimeoutExpired, Exception) as exc:  # noqa: BLE001
        return {"programmatic_warning": (
            f"programmatic input generation failed ({type(exc).__name__}: {str(exc)[:200]}); "
            "kept the model's hand-crafted hidden cases"
        )}

    valid = _valid_inputs(cases, param_names, seen)
    if not valid:
        return {"programmatic_warning": (
            "programmatic generator produced no valid inputs; kept the model's "
            "hand-crafted hidden cases"
        )}

    placeholder = {"result": _placeholder(result_type)}
    replaced = 0
    for slot, inp in zip(hidden_idx, valid):
        tc = raw_testcases[slot]
        tc["inputData"] = inp
        # Old LLM output was for the OLD input — drop it; expected_verifier will
        # overwrite with the proven value. Placeholder keeps the shape valid.
        tc["expectedOutput"] = dict(placeholder)
        tc["note"] = "Hidden — large randomized input (programmatically generated)."
        replaced += 1

    warning = None
    if replaced < len(hidden_idx):
        warning = (
            f"programmatic generator filled {replaced}/{len(hidden_idx)} hidden "
            "cases; remaining hidden cases kept the model's inputs"
        )

    return {"raw_testcases": raw_testcases, "programmatic_warning": warning}
