#!/usr/bin/env python3
"""Live end-to-end smoke test against the REAL judge on prod.

For each chosen problem:
  1) POST /admin/questions/coding   → server question id
  2) PATCH /admin/questions/{id}/status ACTIVE
  3) submit the CORRECT reference solution (python) and a WRONG one through
     practice → Kafka → judge → Judge0 → callback → comparator
  4) poll GET /practice/submissions/{id} and assert: correct=ACCEPTED,
     wrong != ACCEPTED.

This exercises the parts the local harness can't: Judge0 itself, base64 MIME
decode, the Java OutputComparator, and the Kafka verdict round-trip.

  JWT_HMAC_SECRET=... python3 smoke_test.py "Missing Number" "Maximum Depth of Binary Tree" "Reverse Linked List"
"""
import json
import os
import sys
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from mint_token import mint

BASE = os.getenv("BASE_URL", "http://31.220.84.140/api/v1").rstrip("/")
TOKEN = mint()
HERE = Path(__file__).parent


def _refs():
    refs = {}
    for batch in ("easy", "medium", "hard"):
        mod = __import__(f"problems_{batch}")
        for p in mod.PROBLEMS:
            refs[p["title"]] = p["ref"]
    return refs


def http(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = Request(BASE + path, data=data, method=method, headers={
        "Content-Type": "application/json", "Accept": "application/json",
        "Authorization": f"Bearer {TOKEN}"})
    try:
        with urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {}


def wrong_solution(fm):
    fn = fm["fn"]
    params = ", ".join(p["name"] for p in fm["params"])
    # deliberately wrong but well-typed-ish: return a constant of the wrong value
    rt = fm["return"]
    if rt in ("int", "long", "Integer", "Long"):
        ret = "return -999999"
    elif rt in ("boolean", "Boolean"):
        ret = "return False"
    elif rt in ("string", "String", "char", "Character"):
        ret = 'return "__wrong__"'
    else:
        ret = "return []"
    if fm.get("inPlace"):
        ret = "pass"
    return f"class Solution:\n    def {fn}(self, {params}):\n        {ret}\n"


def submit_and_wait(qid, code, label):
    st, payload = http("POST", f"/practice/problems/{qid}/submit",
                       {"language": "python", "code": code})
    sub = (payload.get("data") or {}).get("submissionId")
    if not sub:
        return f"submit failed ({st}): {payload}"
    for _ in range(40):
        time.sleep(1.5)
        s, p = http("GET", f"/practice/submissions/{sub}")
        d = p.get("data") or {}
        status = d.get("status")
        if status in ("COMPLETED", "DONE", "FINISHED") or d.get("verdict"):
            return f"{label}: verdict={d.get('verdict')} passed={d.get('passedCases')}/{d.get('totalCases')}"
    return f"{label}: TIMEOUT (still {status})"


def main():
    titles = sys.argv[1:] or ["Missing Number", "Maximum Depth of Binary Tree",
                              "Reverse Linked List"]
    refs = _refs()
    questions = {q["title"]: q for q in json.load(open(HERE / "coding_questions.json"))}

    for title in titles:
        q = questions[title]
        print(f"\n=== {title} ===")
        st, payload = http("POST", "/question-bank/admin/questions/coding", q)
        if st not in (200, 201) or not payload.get("success", False):
            print(f"  CREATE FAILED ({st}): {payload.get('message') or payload}")
            continue
        qid = (payload.get("data") or {}).get("id")
        print(f"  created id={qid}")
        http("PATCH", f"/question-bank/admin/questions/{qid}/status", {"status": "ACTIVE"})
        time.sleep(1.0)
        print("  " + submit_and_wait(qid, refs[title], "CORRECT"))
        print("  " + submit_and_wait(qid, wrong_solution(q["functionMeta"]), "WRONG  "))


if __name__ == "__main__":
    main()
