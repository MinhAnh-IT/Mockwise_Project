#!/bin/sh
#
# seed-inner.sh — runs INSIDE a curlimages/curl sidecar container that is
# attached to the mockwise docker network. Reads one JSON behavioral-question
# body per line from /data.ndjson, creates it (which triggers Vietnamese TTS +
# storage on question-bank-service), then flips it to ACTIVE (which emits the
# QUESTION_ACTIVATED event → AI-Question-Selector builds its embedding).
#
# Env:
#   BASE     base URL of question-bank, e.g. http://question-bank-service:8084/api/v1/question-bank
#   UID_HDR  value for the X-User-Id provenance header (gateway is bypassed)
#
# Not called directly — launched by seed-behavioral.sh.
#
set -u

: "${BASE:?BASE env is required}"
: "${UID_HDR:=admin-seed}"

total=0; ok=0; fail=0; no_audio=0

while IFS= read -r line; do
  [ -z "$line" ] && continue
  total=$((total + 1))

  # ── Create (DRAFT) — synchronous TTS happens here ──────────────────────────
  resp=$(curl -s -w '\n%{http_code}' -X POST \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: $UID_HDR" \
    --data "$line" \
    "$BASE/admin/questions/behavioral")
  code=$(printf '%s' "$resp" | tail -n1)
  body=$(printf '%s' "$resp" | sed '$d')
  id=$(printf '%s' "$body" | grep -o '"id":"[^"]*"' | head -n1 | sed 's/"id":"//; s/"$//')

  if [ "$code" != "201" ] || [ -z "$id" ]; then
    fail=$((fail + 1))
    echo "FAIL create (http $code): $body"
    continue
  fi

  # A quoted audioKey means TTS succeeded; null/absent means it was saved without audio.
  if ! printf '%s' "$body" | grep -q '"audioKey":"[^"]'; then
    no_audio=$((no_audio + 1))
  fi

  # ── Activate (emits QUESTION_ACTIVATED → embedding) ────────────────────────
  acode=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
    -H 'Content-Type: application/json' \
    -H "X-User-Id: $UID_HDR" \
    --data '{"status":"ACTIVE"}' \
    "$BASE/admin/questions/$id/status")

  if [ "$acode" != "200" ]; then
    fail=$((fail + 1))
    echo "FAIL activate (http $acode) id=$id"
    continue
  fi

  ok=$((ok + 1))
  echo "OK  $id"
done < /data.ndjson

echo "----------------------------------------"
echo "total=$total  ok=$ok  fail=$fail  no_audio=$no_audio"
[ "$fail" -eq 0 ] || { echo "Some rows failed — see FAIL lines above."; exit 1; }
[ "$no_audio" -eq 0 ] || echo "WARN: $no_audio question(s) saved without audio (TTS may be down)."
