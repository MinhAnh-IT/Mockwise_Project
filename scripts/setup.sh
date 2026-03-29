#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Setting up Mockwise dev environment..."

# Copy .env if not exists
if [ ! -f "$ROOT/.env" ]; then
  cp "$ROOT/.env.example" "$ROOT/.env"
  echo "    Created .env from .env.example — update secrets before running!"
fi

# Install deps for each service
for service in api-gateway auth-service user-service product-service; do
  if [ -f "$ROOT/services/$service/package.json" ]; then
    echo "==> Installing deps: services/$service"
    npm ci --prefix "$ROOT/services/$service"
  fi
done

# Install frontend deps
if [ -f "$ROOT/frontend/package.json" ]; then
  echo "==> Installing deps: frontend"
  npm ci --prefix "$ROOT/frontend"
fi

echo ""
echo "Done! Run './scripts/dev.sh' to start all services."
