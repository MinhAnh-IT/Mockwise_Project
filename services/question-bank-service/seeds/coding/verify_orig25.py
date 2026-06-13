#!/usr/bin/env python3
"""Submit a CORRECT solution (python/java/js/cpp) for each of the 25 original
prod questions through the live judge and report the verdict per language.
A WA/RE on a correct solution => the original's AI-generated testcases are wrong.

  JWT_HMAC_SECRET=... python3 verify_orig25.py
"""
import json, time, sys
from urllib.request import Request, urlopen
import urllib.error
from mint_token import mint
from orig25_solutions import SOL

BASE = "http://31.220.84.140/api/v1"
TOK = mint()
LANGS = ["python", "java", "javascript", "cpp"]


def _json(b):
    try:
        return json.loads(b or "{}")
    except Exception:
        return {}


def http(m, p, b=None):
    d = json.dumps(b).encode() if b is not None else None
    r = Request(BASE + p, data=d, method=m, headers={
        "Content-Type": "application/json", "Authorization": f"Bearer {TOK}"})
    try:
        with urlopen(r, timeout=40) as x:
            return x.status, _json(x.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, _json(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception:
        return 0, {}


def title_ids():
    ids = {}
    for ln in open("/tmp/orig25.txt"):
        ln = ln.strip()
        if "~~" not in ln:
            continue
        i, t, _ = ln.split("~~", 2)
        ids[t] = i
    return ids


def submit(qid, code, lang):
    for _ in range(4):
        st, p = http("POST", f"/practice/problems/{qid}/submit",
                     {"language": lang, "code": code})
        sid = (p.get("data") or {}).get("submissionId")
        if sid:
            return sid
        time.sleep(1.0)
    return f"ERR({st})"


def poll(sub):
    s, pp = http("GET", f"/practice/submissions/{sub}")
    d = pp.get("data") or {}
    if d.get("verdict"):
        return f"{d.get('verdict')} {d.get('passedCases')}/{d.get('totalCases')}"
    return None


# judge0 has 4 workers / MAX_QUEUE_SIZE=60; each submission expands to ~24
# testcases. Keep at most IN_FLIGHT submissions live (2*24=48 < 60) so we never
# overflow the queue and trigger 503s, while still feeding all 4 workers.
IN_FLIGHT = 3
PER_TIMEOUT = 240        # seconds to wait for one submission's verdict
POLL_EVERY = 2.0

_print_lock = __import__("threading").Lock()


def run_one(qid, title, lang):
    """Submit one solution and poll until a verdict (or timeout)."""
    sid = submit(qid, SOL[title][lang], lang)
    if str(sid).startswith("ERR"):
        v = sid
    else:
        v = "TIMEOUT"
        deadline = time.time() + PER_TIMEOUT
        while time.time() < deadline:
            time.sleep(POLL_EVERY)
            r = poll(sid)
            if r is not None:
                v = r
                break
    with _print_lock:
        mark = "ok " if str(v).startswith("AC") else "BAD"
        print(f"  [{mark}] {title[:42]:42} {lang:11} -> {v}", flush=True)
    return (title, lang), v


def main():
    from concurrent.futures import ThreadPoolExecutor
    ids = title_ids()
    keys = [(t, l) for t in sorted(SOL) for l in LANGS]
    print(f"submitting {len(keys)} solutions (IN_FLIGHT={IN_FLIGHT}) "
          f"through the live judge...\n", flush=True)

    # bounded pipeline: at most IN_FLIGHT submissions alive at any moment
    results = {}
    with ThreadPoolExecutor(max_workers=IN_FLIGHT) as ex:
        futs = [ex.submit(run_one, ids[t], t, l) for (t, l) in keys]
        for f in futs:
            k, v = f.result()
            results[k] = v

    # report
    print()
    print(f"{'TITLE':44} {'python':12} {'java':12} {'javascript':12} {'cpp':12}")
    print("-" * 96)
    bad = []
    for t in sorted(SOL):
        cells = [results.get((t, l), "?") for l in LANGS]
        for l, v in zip(LANGS, cells):
            if not str(v).startswith("AC"):
                bad.append((t, l, v))
        print(f"{t[:44]:44} {cells[0]:12} {cells[1]:12} {cells[2]:12} {cells[3]:12}")
    print("\n=== NON-AC (correct solution failed -> suspicious original testcases) ===")
    if not bad:
        print("none — all 25 originals accept a correct solution in all 4 languages")
    for t, l, v in bad:
        print(f"  {t}  [{l}] -> {v}")


if __name__ == "__main__":
    main()
