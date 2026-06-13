#!/usr/bin/env python3
"""Verify the 25 ORIGINAL prod questions by running a CORRECT solution
(python/java/js/cpp) through the REAL judge drivers against each question's
STORED testcases, and comparing to the stored expectedOutput with the same
OutputComparator the judge uses.

A WA here means: a correct solution disagrees with the stored expected output
=> the original's AI-generated testcase is wrong, and the live judge would also
reject a correct submission. (Identical verdict logic to prod; no Judge0 needed,
and the four drivers were already proven byte-identical across 2871 cases.)

  python3 verify_orig25_local.py        # all 4 languages
"""
import json
import tempfile
from pathlib import Path

from engine import build_batch_stdin, parse_framed, comparator_accepts, DriverRunner
from verify_drivers import LANGS  # JavaLang / JsLang / CppLang (real drivers)
from orig25_solutions import SOL

DATA = Path("/tmp/orig25_data.jsonl")
# driver registry in verify_drivers.LANGS uses "js" for JavaScript
DRIVER_KEY = {"java": "java", "javascript": "js", "cpp": "cpp"}


def meta_of(fm):
    return {
        "fn": fm["fn"],
        "params": [(p["name"], p["type"]) for p in fm["params"]],
        "return": fm["return"],
        "orderMatters": fm.get("orderMatters", False),
        "inPlace": fm.get("inPlace", False),
    }


def _check_batch(raw, meta, cases):
    framed = parse_framed(raw)
    for i, tc in enumerate(cases):
        status, body = framed[i] if i < len(framed) else ("MISSING", "")
        if status != "OK":
            return ("RE", tc)
        if not comparator_accepts(body, tc["expectedOutput"]["result"], meta["orderMatters"]):
            return ("WA", tc)
    return ("AC", None)


def run_python(ref, meta, cases):
    runner = DriverRunner(ref)
    raw = runner.run(build_batch_stdin(meta, [tc["inputData"] for tc in cases]))
    return _check_batch(raw, meta, cases)


def run_compiled(lang, code, meta, cases):
    with tempfile.TemporaryDirectory() as tmp:
        runner = LANGS[DRIVER_KEY[lang]](tmp)
        try:
            runner.prep("", meta, code)
        except RuntimeError as e:
            return ("CE", str(e)[:120])
        raw = runner.run(build_batch_stdin(meta, [tc["inputData"] for tc in cases]))
        return _check_batch(raw, meta, cases)


def main():
    questions = {}
    for ln in DATA.read_text().splitlines():
        ln = ln.strip()
        if not ln:
            continue
        q = json.loads(ln)
        questions[q["title"]] = q

    print(f"{'TITLE':44} {'python':9} {'java':9} {'javascript':11} {'cpp':9}  n")
    print("-" * 92)
    bad = []
    for title in sorted(questions):
        q = questions[title]
        meta = meta_of(q["functionMeta"])
        cases = q["testCases"]
        cells = []
        for lang in ("python", "java", "javascript", "cpp"):
            code = SOL[title][lang]
            if lang == "python":
                v, tc = run_python(code, meta, cases)
            else:
                v, tc = run_compiled(lang, code, meta, cases)
            cells.append(v)
            if v != "AC":
                bad.append((title, lang, v, tc))
        print(f"{title[:44]:44} {cells[0]:9} {cells[1]:9} {cells[2]:11} {cells[3]:9}  {len(cases)}")

    print("\n=== ORIGINALS WHERE A CORRECT SOLUTION FAILS (bad AI testcases) ===")
    if not bad:
        print("none — all 25 originals accept a correct solution in all 4 languages")
    seen = set()
    for title, lang, v, tc in bad:
        if title in seen:
            continue
        seen.add(title)
        if isinstance(tc, dict):
            inp = json.dumps(tc["inputData"])[:90]
            exp = json.dumps(tc["expectedOutput"]["result"])[:50]
            print(f"  {title}: {v}  input={inp} stored_expected={exp}")
        else:
            print(f"  {title}: {v} {tc}")


if __name__ == "__main__":
    main()
