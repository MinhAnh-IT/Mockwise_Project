#!/usr/bin/env python3
"""
Import seed questions into question-bank-service.

Mỗi câu hỏi sẽ được:
  1) POST tạo (DRAFT) qua admin API
  2) PATCH status=ACTIVE để bắn event Kafka sang AI service embed

Usage:
  python3 import_seeds.py                 # import cả behavioral + core
  python3 import_seeds.py behavioral      # chỉ behavioral
  python3 import_seeds.py core            # chỉ core
  python3 import_seeds.py --dry-run       # xem trước, không gọi API

Env vars:
  BASE_URL    default http://31.220.84.140/api/v1/question-bank
  USER_ID     default 00000000-0000-0000-0000-000000000001 (gửi qua header X-User-Id)
  SLEEP_MS    delay giữa các request (mặc định 100ms tránh dồn Kafka)
"""

import argparse
import json
import os
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE_URL = os.getenv("BASE_URL", "http://31.220.84.140/api/v1/question-bank").rstrip("/")
TOKEN = os.getenv("ADMIN_TOKEN", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5M2E2Nzg5Zi0xMTkyLTQ4MGEtYTQ3OS05NTdiZThiYjRmZGQiLCJhdWQiOiJtb2Nrd2lzZS1hcGkiLCJ2ZXIiOjUsIm5iZiI6MTc3NzUyNTE5NCwicm9sZSI6IkFkbWluIiwiaXNzIjoibW9ja3dpc2UtaWFtIiwiZXhwIjoxNzc3NTMxMjU0LCJpYXQiOjE3Nzc1MjUyNTQsImp0aSI6ImE4MGQ5ZTUxLTcxNTgtNGY5YS1iZGNkLWQ3ZDc4ZTU0MDUwMyIsInVzZXJuYW1lIjoiYW5oaHV5bmgudGVjaEBnbWFpbC5jb20ifQ.e-Wkvi1Z-IdtuBuZ5dVhLkmrvFw-bCpLnuHdmsfmDqM")
SLEEP_MS = int(os.getenv("SLEEP_MS", "100"))

SCRIPT_DIR = Path(__file__).resolve().parent
FILES = {
    "behavioral": SCRIPT_DIR / "behavioral_questions.json",
    "core":       SCRIPT_DIR / "core_questions.json",
    "coding":     SCRIPT_DIR / "coding" / "coding_questions.json",
}


def http_json(method: str, url: str, body=None, timeout: int = 30):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "Authorization": f"Bearer {TOKEN}",
    }
    req = Request(url, data=data, method=method, headers=headers)
    try:
        with urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "{}")
    except HTTPError as e:
        try:
            payload = json.loads(e.read().decode("utf-8") or "{}")
        except Exception:
            payload = {"error": str(e)}
        return e.code, payload
    except URLError as e:
        return 0, {"error": f"network: {e.reason}"}


def create_question(kind: str, body: dict):
    return http_json("POST", f"{BASE_URL}/admin/questions/{kind}", body)


def activate_question(qid: str):
    return http_json("PATCH", f"{BASE_URL}/admin/questions/{qid}/status",
                     body={"status": "ACTIVE"})


def import_file(kind: str, dry_run: bool):
    path = FILES[kind]
    if not path.exists():
        print(f"[skip] {path} không tồn tại")
        return 0, 0

    questions = json.loads(path.read_text(encoding="utf-8"))
    print(f"\n>> {kind}: {len(questions)} câu hỏi  →  {BASE_URL}/admin/questions/{kind}")

    ok, fail = 0, 0
    for i, q in enumerate(questions, 1):
        text_preview = (q.get("text") or q.get("title", ""))[:70].replace("\n", " ")

        if dry_run:
            print(f"  [{i:3d}/{len(questions)}] DRY  {text_preview}")
            ok += 1
            continue

        # 1) Tạo câu hỏi (DRAFT)
        status, payload = create_question(kind, q)
        if status not in (200, 201) or not payload.get("success", False):
            fail += 1
            print(f"  [{i:3d}/{len(questions)}] CREATE FAIL ({status}) "
                  f"{text_preview} :: {payload.get('message') or payload}")
            continue

        qid = (payload.get("data") or {}).get("id")
        if not qid:
            fail += 1
            print(f"  [{i:3d}/{len(questions)}] NO ID in response :: {payload}")
            continue

        # 2) Bật ACTIVE → publisher bắn event Kafka, AI service embed
        status2, payload2 = activate_question(qid)
        if status2 not in (200, 204) or not payload2.get("success", True):
            fail += 1
            print(f"  [{i:3d}/{len(questions)}] ACTIVATE FAIL ({status2}) "
                  f"id={qid} :: {payload2.get('message') or payload2}")
            continue

        ok += 1
        print(f"  [{i:3d}/{len(questions)}] OK  id={qid}  {text_preview}")

        if SLEEP_MS > 0:
            time.sleep(SLEEP_MS / 1000.0)

    print(f"<< {kind} done — ok={ok}  fail={fail}")
    return ok, fail


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", nargs="?", default="all",
                        choices=["all", "behavioral", "core", "coding"])
    parser.add_argument("--dry-run", action="store_true",
                        help="In ra danh sách, không gọi API")
    args = parser.parse_args()

    if not TOKEN and not args.dry_run:
        print("ERROR: ADMIN_TOKEN env var không được để trống")
        sys.exit(2)
    print(f"BASE_URL = {BASE_URL}")
    print(f"TOKEN    = {'<set>' if TOKEN else '<empty>'}")
    print(f"SLEEP_MS = {SLEEP_MS}")
    if args.dry_run:
        print("(dry-run)")

    targets = ["behavioral", "core", "coding"] if args.kind == "all" else [args.kind]
    total_ok = total_fail = 0
    for k in targets:
        ok, fail = import_file(k, args.dry_run)
        total_ok += ok
        total_fail += fail

    print(f"\n=== TỔNG KẾT: ok={total_ok}  fail={total_fail} ===")
    sys.exit(0 if total_fail == 0 else 1)


if __name__ == "__main__":
    main()
