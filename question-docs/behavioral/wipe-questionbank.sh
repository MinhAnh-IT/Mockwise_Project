#!/usr/bin/env bash
#
# wipe-questionbank.sh — DESTRUCTIVE. Wipes the ENTIRE question bank and the
# AI-Question-Selector embedding index on the prod VPS, to make room for a
# fresh question set.
#
# What it deletes:
#   • question_bank.questions  (CASCADE → behavioral/core/coding/question_follow_up)
#   • ai_question_selector.question_index   (pgvector embeddings)
#   • ai_question_selector.processed_events (event-idempotency log)
#
# What it does NOT touch:
#   • flyway_schema_history (migrations stay intact)
#   • storage-service / MinIO audio objects (old TTS files become orphans — see README)
#   • interview_blueprint / sessions
#
# Usage:  ./wipe-questionbank.sh
#         REMOTE_HOST=contabo ./wipe-questionbank.sh   (override ssh host alias)
#
set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:-contabo}"
PG_CONTAINER="${PG_CONTAINER:-mockwise-infra-postgres-1}"
PG_USER="${PG_USER:-mockwise}"

echo "Target host : $REMOTE_HOST"
echo "Postgres    : $PG_CONTAINER (user $PG_USER)"
echo
echo "This will PERMANENTLY DELETE all questions + all embeddings on PROD."
read -r -p "Type 'WIPE' to proceed: " confirm
[ "$confirm" = "WIPE" ] || { echo "Aborted."; exit 1; }

ssh "$REMOTE_HOST" PG_CONTAINER="$PG_CONTAINER" PG_USER="$PG_USER" 'bash -s' <<'REMOTE'
set -euo pipefail
echo "== before =="
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d question_bank \
  -tAc "SELECT 'questions=' || count(*) FROM questions;"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d ai_question_selector \
  -tAc "SELECT 'question_index=' || count(*) FROM question_index;"

echo "== wiping question_bank =="
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d question_bank \
  -c "TRUNCATE questions CASCADE;"

echo "== wiping ai_question_selector embeddings =="
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d ai_question_selector \
  -c "TRUNCATE question_index, processed_events;"

echo "== after =="
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d question_bank \
  -tAc "SELECT 'questions=' || count(*) FROM questions;"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d ai_question_selector \
  -tAc "SELECT 'question_index=' || count(*) FROM question_index;"
echo "Done."
REMOTE
