#!/usr/bin/env python3
"""
Coding-question seed engine.

Builds a `coding_questions.json` (CodingQuestionRequest shape) for the
question-bank `POST /admin/questions/coding` endpoint, and — crucially —
VERIFIES every generated test case by running it through the *real*
`UniversalPythonDriver.py` from judge-service with a reference solution
injected, then checking the verdict with a faithful port of
`OutputComparator`.

Why run the real driver instead of trusting the reference solution's return
value? Because the bugs we fear live in the *serialization round-trip*
(stdin build → driver parse → solve → driver print → JSON compare), exactly
the layer the doc (docs/coding-question-pipeline.md §4) calls "the part most
likely to be wrong". Running the genuine driver closes that gap for Python;
the other three drivers are kept byte-compatible by design (§5 of the doc).

A problem is a dict — see problems_*.py. The engine:
  1. generates inputs (curated examples + randomized hidden cases),
  2. runs the real driver to obtain the canonical expected output,
  3. asserts the driver verdict is ACCEPTED,
  4. enforces the validator invariants (§1.4): input keys == params,
     single "result" key, shape matches return type, no duplicate inputs,
     and (when requested) answer uniqueness,
  5. emits the request JSON + a per-problem verification report.
"""

from __future__ import annotations

import io
import json
import re
import sys
import uuid
from contextlib import redirect_stdout
from pathlib import Path

# ── Locate the real judge driver ──────────────────────────────────────────
REPO = Path(__file__).resolve().parents[4]
DRIVER_PATH = (
    REPO
    / "services/judge-service/src/main/resources/drivers/UniversalPythonDriver.py"
)
MARKER = "# === USER_CODE_INJECTED_HERE ==="

# Deterministic namespace so re-running yields stable question ids (UUIDv5),
# matching the ids already in prod (e.g. Two Sum 490799d1-...).
SEED_NS = uuid.UUID("9b6f3d2a-1c4e-4a7b-8f10-2d5e7c9a0b11")

ARRAY_GROUP = {
    "int[]", "long[]", "double[]", "string[]", "String[]",
    "int[][]", "char[][]", "string[][]", "String[][]",
    "List<Integer>", "List<String>", "List<List<Integer>>", "List<List<String>>",
}
STRINGY_RETURN = {"string", "String", "char", "Character"}


# ── TypeSerializer port (judge codebuilder/TypeSerializer.java) ────────────
def _serialize_param(value, type_):
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


# Record separator for the batch protocol — must match the Universal*Driver files.
RS = "\x1e"


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


def build_batch_stdin(meta: dict, inputs: list) -> str:
    """Mirror StdinBuilder.buildBatch: line 1 = functionMeta JSON, line 2 = T
    (case count), then T blocks of one line per param."""
    lines = [_meta_line(meta), str(len(inputs))]
    for input_data in inputs:
        for name, type_ in meta["params"]:
            lines.append(_serialize_param(input_data[name], type_))
    return "\n".join(lines) + "\n"


def parse_framed(raw: str) -> list:
    """Split RS-framed batch stdout into ordered (status, body) tuples.
    status is "OK" or "ERR"; body is the serialized output (OK) or error (ERR)."""
    out = []
    for chunk in raw.split(RS):
        if chunk == "":
            continue  # leading empty segment before the first RS
        nl = chunk.find("\n")
        if nl < 0:
            out.append((chunk.strip(), ""))
        else:
            out.append((chunk[:nl].strip(), chunk[nl + 1:]))
    return out


def build_stdin(meta: dict, input_data: dict) -> str:
    """Single-case batch (T=1) — convenience for callers verifying one input."""
    return build_batch_stdin(meta, [input_data])


# ── OutputComparator port (judge codebuilder/OutputComparator.java) ────────
def _canonical(node):
    """JsonNode.equals semantics for our value space (lists/scalars/strings)."""
    return json.dumps(node, sort_keys=True, separators=(",", ":"))


def comparator_accepts(stdout: str, expected_result, order_matters: bool) -> bool:
    if stdout is None:
        return False
    raw = stdout.strip()
    try:
        actual = json.loads(raw)
        parsed = True
    except Exception:
        actual = None
        parsed = False

    if not parsed or actual is None:
        # fallback string compare: expected.asText() vs stdout.trim()
        exp_str = expected_result if isinstance(expected_result, str) else _canonical(expected_result)
        return raw == exp_str.strip()

    if (not order_matters) and isinstance(actual, list) and isinstance(expected_result, list):
        sa = sorted(actual, key=lambda n: json.dumps(n, separators=(",", ":")))
        se = sorted(expected_result, key=lambda n: json.dumps(n, separators=(",", ":")))
        return _canonical(sa) == _canonical(se)

    return _canonical(actual) == _canonical(expected_result)


# ── Real-driver runner (in-process for speed) ──────────────────────────────
class DriverRunner:
    """Compile the real Python driver once per problem (user code injected),
    then execute it per test case via redirected stdin/stdout."""

    def __init__(self, ref_code: str):
        src = DRIVER_PATH.read_text(encoding="utf-8")
        if MARKER not in src:
            raise RuntimeError(f"marker not found in {DRIVER_PATH}")
        src = src.replace(MARKER, ref_code)
        # Strip the __main__ guard call; we invoke _main() ourselves.
        src = src.replace('if __name__ == "__main__":\n    _main()', "")
        self.ns: dict = {}
        code = compile(src, str(DRIVER_PATH), "exec")
        exec(code, self.ns)  # noqa: S102 — trusted, repo-local driver + our ref

    def run(self, stdin_str: str) -> str:
        old_stdin = sys.stdin
        buf = io.StringIO()
        sys.stdin = io.StringIO(stdin_str)
        try:
            with redirect_stdout(buf):
                self.ns["_main"]()
        finally:
            sys.stdin = old_stdin
        return buf.getvalue()


# ── Expected-output derivation ─────────────────────────────────────────────
def expected_from_stdout(stdout: str, return_type: str):
    raw = stdout.rstrip("\n")
    if return_type in STRINGY_RETURN:
        return raw  # raw string, no quotes — stored as JSON string
    return json.loads(raw)


# ── Shape validation (validator §1.4) ──────────────────────────────────────
def _shape_ok(meta: dict, result) -> bool:
    rt = meta["return"]
    if meta.get("inPlace"):
        # in-place: result must match params[0] shape (an array group)
        p0t = meta["params"][0][1]
        return isinstance(result, list) == (p0t in ARRAY_GROUP)
    if rt in ARRAY_GROUP or rt in ("TreeNode", "ListNode"):
        return isinstance(result, list)
    if rt in ("boolean", "Boolean"):
        return isinstance(result, bool)
    if rt in ("int", "long", "Integer", "Long", "double", "Double"):
        return isinstance(result, (int, float)) and not isinstance(result, bool)
    if rt in STRINGY_RETURN:
        return isinstance(result, str)
    return True


# ── Starter-code generation ────────────────────────────────────────────────
_PY = {
    "int": "int", "long": "int", "Integer": "int", "Long": "int",
    "double": "float", "Double": "float", "boolean": "bool", "Boolean": "bool",
    "char": "str", "Character": "str", "string": "str", "String": "str",
    "int[]": "list[int]", "long[]": "list[int]", "double[]": "list[float]",
    "string[]": "list[str]", "String[]": "list[str]",
    "int[][]": "list[list[int]]", "char[][]": "list[list[str]]",
    "string[][]": "list[list[str]]", "String[][]": "list[list[str]]",
    "List<Integer>": "list[int]", "List<String>": "list[str]",
    "List<List<Integer>>": "list[list[int]]", "List<List<String>>": "list[list[str]]",
    "TreeNode": "Optional[TreeNode]", "ListNode": "Optional[ListNode]", "void": "None",
}
_JAVA = {
    "string": "String", "char": "char", "void": "void",
}
_CPP = {
    "int": "int", "long": "long long", "Integer": "int", "Long": "long long",
    "double": "double", "Double": "double", "boolean": "bool", "Boolean": "bool",
    "char": "char", "Character": "char", "string": "string", "String": "string",
    "int[]": "vector<int>&", "long[]": "vector<long long>&", "double[]": "vector<double>&",
    "string[]": "vector<string>&", "String[]": "vector<string>&",
    "int[][]": "vector<vector<int>>&", "char[][]": "vector<vector<char>>&",
    "string[][]": "vector<vector<string>>&", "String[][]": "vector<vector<string>>&",
    "List<Integer>": "vector<int>&", "List<String>": "vector<string>&",
    "List<List<Integer>>": "vector<vector<int>>&", "List<List<String>>": "vector<vector<string>>&",
    "TreeNode": "TreeNode*", "ListNode": "ListNode*", "void": "void",
}
_CPP_RET = {**_CPP, "int[]": "vector<int>", "long[]": "vector<long long>",
            "double[]": "vector<double>", "string[]": "vector<string>",
            "String[]": "vector<string>", "int[][]": "vector<vector<int>>",
            "char[][]": "vector<vector<char>>", "string[][]": "vector<vector<string>>",
            "String[][]": "vector<vector<string>>", "List<Integer>": "vector<int>",
            "List<String>": "vector<string>", "List<List<Integer>>": "vector<vector<int>>",
            "List<List<String>>": "vector<vector<string>>"}


def _java_type(t):
    return _JAVA.get(t, t)


def _java_default(rt):
    if rt == "void":
        return None
    if rt in ("int", "long", "Integer", "Long"):
        return "return 0;"
    if rt in ("double", "Double"):
        return "return 0.0;"
    if rt in ("boolean", "Boolean"):
        return "return false;"
    if rt in ("char", "Character"):
        return "return ' ';"
    if rt in ("string", "String"):
        return 'return "";'
    if rt == "int[]":
        return "return new int[]{};"
    if rt == "long[]":
        return "return new long[]{};"
    if rt == "double[]":
        return "return new double[]{};"
    if rt in ("string[]", "String[]"):
        return "return new String[]{};"
    return "return null;"


def gen_starter(meta: dict) -> dict:
    fn = meta["fn"]
    params = meta["params"]
    rt = meta["return"]

    # Python
    py_params = ", ".join(f"{n}: {_PY.get(t, 'object')}" for n, t in params)
    py_ret = _PY.get(rt, "object")
    py = (f"class Solution:\n    def {fn}(self, {py_params}) -> {py_ret}:\n        pass")

    # Java
    jparams = ", ".join(f"{_java_type(t)} {n}" for n, t in params)
    jret = _java_type(rt)
    jdef = _java_default(rt)
    jbody = "        // TODO\n" + (f"        {jdef}\n" if jdef else "")
    java = (f"class Solution {{\n    public {jret} {fn}({jparams}) {{\n{jbody}    }}\n}}")

    # C++
    cparams = ", ".join(f"{_CPP.get(t, 'auto')} {n}" for n, t in params)
    cret = _CPP_RET.get(rt, "auto")
    cdef = "" if rt == "void" else f"        return {{}};\n" if cret.startswith("vector") else (
        "        return nullptr;\n" if cret.endswith("*") else
        '        return "";\n' if cret == "string" else
        "        return false;\n" if cret == "bool" else
        "        return ' ';\n" if cret == "char" else "        return 0;\n")
    cpp = (f"class Solution {{\npublic:\n    {cret} {fn}({cparams}) {{\n"
           f"        // TODO\n{cdef}    }}\n}};")

    # JavaScript
    jsdoc = "\n".join(f" * @param {{{_js_doc(t)}}} {n}" for n, t in params)
    js_ret = _js_doc(rt)
    js_args = ", ".join(n for n, _ in params)
    js = (f"/**\n{jsdoc}\n * @return {{{js_ret}}}\n */\n"
          f"var {fn} = function({js_args}) {{\n    // TODO\n}};")

    return {"python": py, "java": java, "cpp": cpp, "javascript": js}


def _js_doc(t):
    m = {
        "int": "number", "long": "number", "double": "number", "Integer": "number",
        "Long": "number", "Double": "number", "boolean": "boolean", "Boolean": "boolean",
        "char": "character", "string": "string", "String": "string", "void": "void",
        "int[]": "number[]", "long[]": "number[]", "double[]": "number[]",
        "string[]": "string[]", "String[]": "string[]",
        "int[][]": "number[][]", "char[][]": "character[][]", "string[][]": "string[][]",
        "String[][]": "string[][]", "List<Integer>": "number[]", "List<String>": "string[]",
        "List<List<Integer>>": "number[][]", "List<List<String>>": "string[][]",
        "TreeNode": "TreeNode", "ListNode": "ListNode",
    }
    return m.get(t, "*")


# ── Per-problem build + verify ─────────────────────────────────────────────
class VerifyError(Exception):
    pass


def build_problem(p: dict, rng) -> dict:
    meta = {
        "fn": p["fn"],
        "params": p["params"],
        "return": p["return"],
        "orderMatters": p.get("orderMatters", False),
        "inPlace": p.get("inPlace", False),
    }
    param_names = {n for n, _ in p["params"]}
    runner = DriverRunner(p["ref"])

    # Collect (inputData, note, hidden) tuples: examples visible, generated hidden.
    raw_cases = []
    for ex in p.get("examples", []):
        raw_cases.append((ex["input"], ex.get("note"), False))
    for inp in p["gen"](rng):
        raw_cases.append((inp, None, True))

    seen = set()
    test_cases = []
    n_visible = 0
    for input_data, note, hidden in raw_cases:
        if set(input_data.keys()) != param_names:
            raise VerifyError(
                f"{p['title']}: input keys {set(input_data.keys())} != params {param_names}")
        key = json.dumps(input_data, sort_keys=True, separators=(",", ":"))
        if key in seen:
            continue  # dedupe identical inputs
        seen.add(key)

        stdin_str = build_stdin(meta, input_data)
        framed = parse_framed(runner.run(stdin_str))
        if len(framed) != 1 or framed[0][0] != "OK":
            raise VerifyError(f"{p['title']}: driver did not return OK for input={input_data}: {framed}")
        stdout = framed[0][1]
        result = expected_from_stdout(stdout, p["return"])

        if not _shape_ok(meta, result):
            raise VerifyError(
                f"{p['title']}: result shape mismatch for return={p['return']}: {result!r}")

        expected_output = {"result": result}
        if not comparator_accepts(stdout, result, meta["orderMatters"]):
            raise VerifyError(f"{p['title']}: self-verify failed input={input_data}")

        # optional answer-uniqueness guard (e.g. Two Sum): caller-provided checker
        chk = p.get("unique")
        if chk is not None and not chk(input_data, result):
            raise VerifyError(f"{p['title']}: non-unique answer for input={input_data}")

        tc = {
            "id": str(uuid.uuid4()),
            "inputData": input_data,
            "expectedOutput": expected_output,
            "is_hidden": hidden,
        }
        if note and not hidden:
            tc["note"] = note
        if not hidden:
            n_visible += 1
        test_cases.append(tc)

    if len(test_cases) < 20:
        raise VerifyError(f"{p['title']}: only {len(test_cases)} cases (<20)")
    if n_visible < 2:
        raise VerifyError(f"{p['title']}: only {n_visible} visible examples (<2)")

    qid = str(uuid.uuid5(SEED_NS, p["title"]))
    request = {
        "difficulty": p["difficulty"],
        "tags": p.get("tags", []),
        "title": p["title"],
        "description": p["description"],
        "constraints": p.get("constraints", ""),
        "optimalTimeComplexity": p["time"],
        "optimalSpaceComplexity": p["space"],
        "functionMeta": {
            "fn": p["fn"],
            "params": [{"name": n, "type": t} for n, t in p["params"]],
            "return": p["return"],
            "orderMatters": p.get("orderMatters", False),
            "inPlace": p.get("inPlace", False),
        },
        "starterCode": p.get("starterCode") or gen_starter(meta),
        "testCases": test_cases,
    }
    request["_id"] = qid  # internal, stripped before POST
    request["_nVisible"] = n_visible
    request["_nTotal"] = len(test_cases)
    return request


def run_build(problems: list, out_path: Path, rng) -> list:
    titles = [p["title"] for p in problems]
    dups = {t for t in titles if titles.count(t) > 1}
    if dups:
        raise VerifyError(f"duplicate titles in problem set: {dups}")

    built = []
    print(f"Building {len(problems)} problems → verifying each against real driver\n")
    for i, p in enumerate(problems, 1):
        req = build_problem(p, rng)
        built.append(req)
        print(f"  [{i:3d}/{len(problems)}] OK  {p['difficulty']:<6} "
              f"{p['title'][:46]:<46} vis={req['_nVisible']:>2} tot={req['_nTotal']:>2}")

    by_diff = {}
    for r in built:
        by_diff[r["difficulty"]] = by_diff.get(r["difficulty"], 0) + 1
    print(f"\nTotal: {len(built)}  ({by_diff})")
    total_tc = sum(r["_nTotal"] for r in built)
    print(f"Test cases generated & verified: {total_tc}")

    public = [{k: v for k, v in r.items() if not k.startswith("_")} for r in built]
    out_path.write_text(json.dumps(public, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nWrote {out_path}")
    return built
