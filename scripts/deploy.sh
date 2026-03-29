#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Deploying Mockwise (production)..."

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
