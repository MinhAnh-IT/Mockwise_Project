#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker compose \
  -f "$ROOT/docker/docker-compose.yml" \
  -f "$ROOT/docker/docker-compose.dev.yml" \
  --env-file "$ROOT/.env" \
  up --build "$@"
