#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Deploying Mockwise (production)..."

# Ensure shared network between mockwise and judge0 stacks exists
# (idempotent — safe to run on every deploy)
docker network create mockwise-judge-net 2>/dev/null || true
echo "    Network 'mockwise-judge-net' ready."

docker compose \
  -f "$ROOT/docker/docker-compose.prod.yml" \
  --env-file "$ROOT/.env" \
  pull

docker compose \
  -f "$ROOT/docker/docker-compose.prod.yml" \
  --env-file "$ROOT/.env" \
  up -d --remove-orphans

docker image prune -f

echo "==> Deploy complete."
