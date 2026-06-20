"""
Expected-output verifier — the correctness oracle for generated testcases.

The LLM cannot be trusted to *compute* `expectedOutput` (it drops digits in long
arithmetic, miscounts long arrays — the Add Two Numbers II bug). Neither can we
blindly trust a SINGLE reference solution: if that one solution is wrong, every
expected output would be "fixed" to the same wrong value.

So this node uses **dual-solution consensus** executed on the **real judge
Python driver** (the same driver that grades candidates → byte-compatible across
all four languages):

  • the analyzer emits TWO independent solutions — `reference_solution` (optimal)
    and `brute_force_solution` (a simple, obviously-correct approach);
  • both are run on every generated `inputData`;
  • an output is treated as CORRECT only where the two AGREE (consensus). Those
    cases overwrite `expectedOutput` with the proven value — fixing the LLM's
    miscomputations;
  • where they DISAGREE (or a solution errors), the answer is UNKNOWN — we never
    silently pick one. The solutions are regenerated (repair loop); if still
    unresolved after retries, those cases keep the LLM value and are flagged
    LOUDLY in the response `warning` for the admin — errors are surfaced, never
    swallowed.

Execution runs in a subprocess with a wall-clock timeout so a runaway/looping
solution is abandoned (its case falls back to "unverified") instead of hanging
the request worker. Disable the whole step with VERIFY_EXPECTED_OUTPUTS=false.
"""

import json
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import config

# Bundled copy of services/judge-service/.../UniversalPythonDriver.py — the AI
# Docker image only ships the AI/ tree, so we cannot reach the judge-service
# path at runtime. Keep this copy in sync (see drivers/SYNC.md); a CI md5 check
# guards drift.
_BUNDLED_DRIVER = Path(__file__).resolve().parent.parent / "drivers" / "UniversalPythonDriver.py"
_MARKER = "# === USER_CODE_INJECTED_HERE ==="

# Return types whose driver output is a raw (unquoted) string rather than JSON.
_STRINGY_RETURN = {"string", "String", "char", "Character"}

# Record separator framing each case in the batch protocol (matches the drivers).
_RS = "\x1e"


# ── TypeSerializer port (judge codebuilder/TypeSerializer.java) ────────────────
def _serialize_param(value, type_: str) -> str:
    if value is None:
        return "null"
    if type_ in ("int", "long", "Integer", "Long"):
        return str(int(value))
    if type_ in ("double", "Double"):
        return repr(float(value))
    if type_ in ("boolean", "Boolean"):
        return "true" if value else "false"
    if type_ in ("string", "String", "char", "Character"):
        return str(value)
    # arrays / matrices / lists / TreeNode / ListNode → compact JSON
    return json.dumps(value, separators=(",", ":"))


def _meta_line(meta: dict) -> str:
    return json.dumps(
        {
            "fn": meta["fn"],
            "params": [{"name": n, "type": t} for (n, t) in meta["params"]],
            "return": meta["return"],
            "inPlace": meta.get("inPlace", False),
            "orderMatters": meta.get("orderMatters", False),
        },
        separators=(",", ":"),
    )


def _build_batch_stdin(meta: dict, inputs: list) -> str:
    """Mirror StdinBuilder.buildBatch: line 1 = functionMeta JSON, line 2 = T,
    then T blocks of one serialized line per param (in declaration order)."""
    lines = [_meta_line(meta), str(len(inputs))]
    for input_data in inputs:
        for name, type_ in meta["params"]:
            lines.append(_serialize_param(input_data.get(name), type_))
    return "\n".join(lines) + "\n"


def _parse_framed(raw: str) -> list:
    """Split RS-framed batch stdout into ordered (status, body) tuples."""
    out = []
    for chunk in raw.split(_RS):
        if chunk == "":
            continue  # leading empty segment before the first RS
        nl = chunk.find("\n")
        if nl < 0:
            out.append((chunk.strip(), ""))
        else:
            out.append((chunk[:nl].strip(), chunk[nl + 1:]))
    return out


def _expected_from_stdout(body: str, return_type: str):
    raw = body.rstrip("\n")
    if return_type in _STRINGY_RETURN:
        return raw  # raw string, stored as a JSON string
    return json.loads(raw)


def _results_match(a, b, order_matters: bool) -> bool:
    """Compare two `result` values with the judge OutputComparator's semantics:
    exact equality, except unordered arrays are compared as multisets."""
    if isinstance(a, list) and isinstance(b, list) and not order_matters:
        try:
            key = lambda x: json.dumps(x, sort_keys=True, separators=(",", ":"))
            return sorted(a, key=key) == sorted(b, key=key)
        except TypeError:
            return a == b
    return a == b


class _ReferenceError(Exception):
    """Reference solution could not be executed at all (bad source / timeout /
    driver missing) — distinct from a per-case runtime error."""


def _build_full_source(ref_code: str) -> str:
    src = _BUNDLED_DRIVER.read_text(encoding="utf-8")
    if _MARKER not in src:
        raise _ReferenceError(f"injection marker not found in {_BUNDLED_DRIVER}")
    return src.replace(_MARKER, ref_code)


def _run_driver(ref_code: str, stdin_str: str, timeout: int) -> str:
    """Execute the driver (reference injected) in a SUBPROCESS with a wall-clock
    timeout. A subprocess isolates the (LLM-written, untrusted-ish) reference: a
    runaway loop is killed at ``timeout`` and a hard crash cannot take down the
    request worker. Returns the raw RS-framed stdout."""
    full_source = _build_full_source(ref_code)
    with tempfile.NamedTemporaryFile("w", suffix=".py", delete=True) as f:
        f.write(full_source)
        f.flush()
        try:
            proc = subprocess.run(
                [sys.executable, f.name],
                input=stdin_str,
                capture_output=True,
                text=True,
                timeout=timeout,
            )
        except subprocess.TimeoutExpired as exc:
            raise _ReferenceError(f"reference execution timed out after {timeout}s") from exc
    return proc.stdout


def _run_one_case(solution: str, meta: dict, input_data: dict, result_type: str, timeout: int):
    """Run one solution on ONE input; return ``(ok: bool, value_or_error)``."""
    try:
        raw = _run_driver(solution, _build_batch_stdin(meta, [input_data]), timeout)
    except _ReferenceError as exc:
        return (False, str(exc))
    except Exception as exc:  # noqa: BLE001
        return (False, f"driver run failed ({exc})")
    frames = _parse_framed(raw)
    if not frames:
        return (False, "no driver output")
    status, body = frames[0]
    if status != "OK":
        return (False, body.strip() or "solution raised")
    try:
        return (True, _expected_from_stdout(body, result_type))
    except Exception as exc:  # noqa: BLE001
        return (False, f"unparseable driver output: {exc}")


def _run_solution(solution: str, meta: dict, inputs: list, result_type: str, timeout: int) -> list:
    """Run one solution over `inputs` and return a per-case list of
    ``(ok: bool, value_or_error)`` in input order. Each case is its OWN subprocess
    (so a timeout/crash on a heavy input — common for a slow brute force — only
    loses THAT case), and the cases run CONCURRENTLY via a thread pool: the threads
    just wait on subprocess I/O, so a 100-case batch finishes in seconds instead of
    spawning 200 subprocesses serially. ``executor.map`` preserves order."""
    if not inputs:
        return []
    workers = max(1, min(getattr(config, "EXPECTED_VERIFY_WORKERS", 6), len(inputs)))
    with ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(
            lambda inp: _run_one_case(solution, meta, inp, result_type, timeout),
            inputs,
        ))


def _meta_from_analysis(analysis: dict) -> dict:
    return {
        "fn": analysis["fn"],
        "params": [(p["name"], p["type"]) for p in analysis.get("params", [])],
        "return": analysis.get("return_type", ""),
        "inPlace": bool(analysis.get("in_place", False)),
        "orderMatters": bool(analysis.get("order_matters", False)),
    }


def verify_and_fix_expected(analysis: dict, raw_testcases: list) -> dict:
    """Dual-solution consensus verification of every testcase's expectedOutput.

    Runs both `reference_solution` and `brute_force_solution` on each inputData
    via the real driver and classifies each case:
      • CONSENSUS  — both solutions ran and agreed → proven-correct answer;
      • SOLO       — only one solution produced a result (e.g. brute timed out on
                     a large hidden input) → tentatively trusted, flagged;
      • UNRESOLVED — they disagreed, or both failed → answer unknown.

    CONSENSUS (and SOLO) cases are OVERWRITTEN in ``raw_testcases`` with the
    proven value — fixing the LLM's miscomputations. UNRESOLVED cases are left
    untouched (never silently guessed).

    Returns a dict:
      {"testcases", "unresolved": [(id, reason)], "fixed": [id...],
       "solo": [id...], "skip_reason": str|None, "retryable": bool}
    The node decides retry-vs-ship from ``unresolved`` and the retry budget.
    """
    primary = (analysis.get("reference_solution") or "").strip()
    brute = (analysis.get("brute_force_solution") or "").strip()
    if not primary:
        return {"testcases": raw_testcases, "unresolved": [], "fixed": [], "solo": [],
                "skip_reason": "no reference_solution produced by analyzer", "retryable": True}

    # in_place=true: the driver prints params[0] post-mutation, so derive the
    # expected from that param's type rather than the (void) return type.
    if analysis.get("in_place") and analysis.get("params"):
        result_type = analysis["params"][0]["type"]
    else:
        result_type = analysis.get("return_type", "")

    order_matters = bool(analysis.get("order_matters", False))
    meta = _meta_from_analysis(analysis)
    inputs = [tc.get("inputData", {}) for tc in raw_testcases]
    timeout = getattr(config, "EXPECTED_VERIFY_TIMEOUT_SECONDS", 15)

    p_runs = _run_solution(primary, meta, inputs, result_type, timeout)
    b_runs = _run_solution(brute, meta, inputs, result_type, timeout) if brute else None

    unresolved: list[tuple[str, str]] = []
    fixed: list[str] = []
    solo: list[str] = []

    for i, tc in enumerate(raw_testcases):
        tc_id = str(tc.get("id", i))
        p_ok, p_val = p_runs[i]
        b_ok, b_val = (b_runs[i] if b_runs is not None else (False, "no brute-force solution"))

        if p_ok and b_ok:
            if _results_match(p_val, b_val, order_matters):
                proven = p_val                       # CONSENSUS
            else:
                unresolved.append((tc_id, "reference and brute-force disagree"))
                continue
        elif p_ok and not b_ok:
            proven = p_val                           # SOLO (brute unavailable)
            if brute:
                solo.append(tc_id)
        elif b_ok and not p_ok:
            proven = b_val                           # SOLO (reference failed on this case)
            solo.append(tc_id)
        else:
            unresolved.append((tc_id, f"both solutions failed (ref: {p_val}; brute: {b_val})"))
            continue

        old = tc.get("expectedOutput") or {}
        if not isinstance(old, dict) or not _results_match(old.get("result"), proven, order_matters):
            fixed.append(tc_id)
        tc["expectedOutput"] = {"result": proven}

    return {"testcases": raw_testcases, "unresolved": unresolved, "fixed": fixed,
            "solo": solo, "skip_reason": None, "retryable": True}


def _build_warning(res: dict, final: bool) -> str | None:
    """Compose the admin-facing warning from a verification result."""
    parts: list[str] = []
    if res["skip_reason"]:
        parts.append(f"expected-output verification skipped: {res['skip_reason']}")
    if res["fixed"]:
        parts.append(
            f"corrected {len(res['fixed'])} expectedOutput(s) the AI had computed wrong, "
            f"via verified solution execution [{', '.join(res['fixed'])}]"
        )
    if res["solo"]:
        parts.append(
            f"{len(res['solo'])} case(s) verified by one solution only (brute-force "
            f"skipped/too slow) [{', '.join(res['solo'])}]"
        )
    if res["unresolved"]:
        detail = "; ".join(f"{cid}: {why}" for cid, why in res["unresolved"])
        if final:
            parts.append(
                f"⚠ COULD NOT VERIFY {len(res['unresolved'])} case(s) after retries — kept the "
                f"AI's UNVERIFIED expectedOutput, REVIEW MANUALLY before saving [{detail}]"
            )
        else:
            parts.append(f"{len(res['unresolved'])} case(s) unresolved [{detail}]")
    return "; ".join(parts) if parts else None


# ── Node ──────────────────────────────────────────────────────────────────────

def expected_verifier_node(state) -> dict:
    """Graph node: between testcase_generator and testcase_validator.

    Verifies expectedOutput by dual-solution consensus and OVERWRITES the cases
    it can prove. If any case is unresolved (solutions disagree / both fail) and
    the reference-retry budget remains, it requests a regeneration of the
    solutions (``needs_reference_retry``) with feedback; otherwise it ships,
    keeping unresolved cases' LLM values but flagging them loudly so nothing is
    swallowed.
    """
    if not getattr(config, "VERIFY_EXPECTED_OUTPUTS", True):
        return {}

    analysis = state.get("problem_analysis")
    raw_testcases = state.get("raw_testcases")
    if not analysis or not raw_testcases:
        return {}

    res = verify_and_fix_expected(analysis, raw_testcases)
    retry_count = state.get("reference_retry_count", 0)
    max_retries = getattr(config, "REFERENCE_MAX_RETRIES", 2)

    # Repair loop: regenerate the solutions when something is unverifiable
    # (disagreement / failure / missing reference) and budget remains.
    needs_fix = bool(res["unresolved"]) or bool(res["skip_reason"])
    if needs_fix and retry_count < max_retries:
        if res["skip_reason"]:
            feedback = f"Reference verification could not run: {res['skip_reason']}."
        else:
            detail = "; ".join(f"{cid}: {why}" for cid, why in res["unresolved"])
            feedback = (
                "The reference_solution and brute_force_solution disagreed or errored on "
                f"these inputs, so they cannot both be correct — fix BOTH so they agree on "
                f"every input and handle large/edge cases: {detail}"
            )
        return {
            "raw_testcases": res["testcases"],
            "needs_reference_retry": True,
            "reference_retry_count": retry_count + 1,
            "reference_feedback": feedback,
        }

    return {
        "raw_testcases": res["testcases"],
        "needs_reference_retry": False,
        "verifier_warning": _build_warning(res, final=True),
    }
