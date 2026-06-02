#!/usr/bin/env bash
#
# seed-behavioral.sh — creates the new Vietnamese behavioral question set on the
# prod VPS via the question-bank ADMIN API (so each question gets TTS audio +
# storage, and gets embedded by the AI-Question-Selector on activation).
#
# It copies the dataset + inner loop to the VPS, then runs a throwaway
# curlimages/curl container on the mockwise docker network (the question-bank
# service is not published to the host). The gateway is bypassed, so no admin
# JWT is needed — the controller only reads the X-User-Id provenance header.
#
# Run AFTER wipe-questionbank.sh.
#
# Usage:  ./seed-behavioral.sh
#         REMOTE_HOST=contabo ./seed-behavioral.sh
#
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
REMOTE_HOST="${REMOTE_HOST:-contabo}"
NET="${NET:-mockwise_internal-net}"
QB_BASE="${QB_BASE:-http://question-bank-service:8084/api/v1/question-bank}"
UID_HDR="${UID_HDR:-admin-seed}"
CURL_IMAGE="${CURL_IMAGE:-curlimages/curl:latest}"

NDJSON="$DIR/behavioral-questions.ndjson"
INNER="$DIR/seed-inner.sh"
[ -f "$NDJSON" ] || { echo "Missing $NDJSON"; exit 1; }
[ -f "$INNER" ]  || { echo "Missing $INNER"; exit 1; }

echo "Host     : $REMOTE_HOST"
echo "Network  : $NET"
echo "QB base  : $QB_BASE"
echo "Questions: $(grep -c . "$NDJSON")"
echo

echo "== copying dataset + inner script to VPS =="
scp "$NDJSON" "$INNER" "$REMOTE_HOST:/tmp/"

echo "== seeding (create + TTS + activate) =="
ssh "$REMOTE_HOST" \
  NET="$NET" QB_BASE="$QB_BASE" UID_HDR="$UID_HDR" CURL_IMAGE="$CURL_IMAGE" \
  'docker run --rm \
     --network "$NET" \
     -e BASE="$QB_BASE" \
     -e UID_HDR="$UID_HDR" \
     -v /tmp/behavioral-questions.ndjson:/data.ndjson:ro \
     -v /tmp/seed-inner.sh:/seed-inner.sh:ro \
     --entrypoint sh "$CURL_IMAGE" /seed-inner.sh'
