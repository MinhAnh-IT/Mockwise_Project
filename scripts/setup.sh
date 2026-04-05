#!/usr/bin/env bash
# First-time server setup for Mockwise production environment.
#
# Run this ONCE on a fresh VPS before the first deploy:
#   bash scripts/setup.sh
#
# What it does:
#   1. Creates .env from .env.example (if not exists)
#   2. Creates the shared Docker network for judge0 <-> judge-service
#   3. Reminds you to set up Judge0 stack separately
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Mockwise — first-time server setup"

# ── 1. Environment file ───────────────────────────────────────────
if [ ! -f "$ROOT/.env" ]; then
  cp "$ROOT/.env.example" "$ROOT/.env"
  echo "    Created .env from .env.example"
  echo "    !! Edit .env and fill in all secrets before deploying !!"
else
  echo "    .env already exists — skipping."
fi

# ── 2. Shared Docker network ──────────────────────────────────────
docker network create mockwise-judge-net 2>/dev/null \
  && echo "    Created Docker network: mockwise-judge-net" \
  || echo "    Network 'mockwise-judge-net' already exists — skipping."

# ── 3. Remind about Judge0 ───────────────────────────────────────
echo ""
echo "==> Setup complete."
echo ""
echo "Next steps:"
echo "  1. Edit .env with your actual secrets"
echo "  2. Deploy Judge0 stack and make sure it joins 'mockwise-judge-net':"
echo "       cd /path/to/judge0"
echo "       docker compose up -d"
echo "  3. Run the first deploy:"
echo "       bash scripts/deploy.sh"
