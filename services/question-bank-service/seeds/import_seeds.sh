#!/usr/bin/env bash
# Import seed questions into question-bank-service via admin API.
#
# Usage:
#   BASE_URL=http://localhost:8084/api/v1/question-bank \
#   USER_ID=00000000-0000-0000-0000-000000000001 \
#   ./import_seeds.sh                     # import both files
#   ./import_seeds.sh behavioral          # only behavioral
#   ./import_seeds.sh core                # only core
#
# Defaults assume question-bank-service running directly on :8084.
# Use BASE_URL=http://localhost:8888/api/v1/question-bank for the gateway.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8084/api/v1/question-bank}"
USER_ID="${USER_ID:-00000000-0000-0000-0000-000000000001}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

command -v jq >/dev/null   || { echo "jq required";   exit 1; }
command -v curl >/dev/null || { echo "curl required"; exit 1; }

import_file() {
  local kind="$1"           # behavioral | core
  local file="$SCRIPT_DIR/${kind}_questions.json"
  local endpoint="$BASE_URL/admin/questions/$kind"
  local total ok=0 fail=0

  [[ -f "$file" ]] || { echo "missing $file"; return 1; }
  total=$(jq 'length' "$file")
  echo ">> $kind: $total questions → $endpoint"

  for i in $(seq 0 $((total - 1))); do
    local body status
    body=$(jq -c ".[$i]" "$file")
    status=$(curl -s -o /tmp/qb_seed_resp -w "%{http_code}" \
      -X POST "$endpoint" \
      -H "Content-Type: application/json" \
      -H "X-User-Id: $USER_ID" \
      -d "$body")
    if [[ "$status" == "201" || "$status" == "200" ]]; then
      ok=$((ok + 1))
      printf "."
    else
      fail=$((fail + 1))
      echo
      echo "  [FAIL #$i status=$status] $(jq -r '.text' <<<"$body" | cut -c1-80)"
      cat /tmp/qb_seed_resp; echo
    fi
  done
  echo
  echo "<< $kind done: ok=$ok fail=$fail"
}

case "${1:-all}" in
  behavioral) import_file behavioral ;;
  core)       import_file core ;;
  all)        import_file behavioral; import_file core ;;
  *) echo "unknown arg: $1 (use behavioral|core|all)"; exit 1 ;;
esac
