#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
# MockWise host firewall setup.  *** READ THIS BEFORE RUNNING ***
#
# Two things to know about Docker + firewalls:
#   1. Docker publishes container ports straight into iptables, BYPASSING ufw.
#      So a normal `ufw deny 9000` does NOT block a docker-published 9000.
#      The fix is rules in the DOCKER-USER chain (handled below).
#   2. The real fix for infra (MinIO/DB/Judge0) is to NOT publish them on
#      0.0.0.0 at all — bind to 127.0.0.1 in their compose. This script is a
#      safety net, not a substitute for that.
#
# What this does:
#   • ufw: allow SSH(22)+HTTP(80)+HTTPS(443), default-deny the rest.
#   • DOCKER-USER: drop sensitive infra ports from any non-local source.
#   • (optional) restrict 80/443 to Cloudflare ranges only — run with
#     CLOUDFLARE_ONLY=1 *after* DNS is proxied through Cloudflare, or you
#     will lock the site off from the public.
#
# Safety: KEEP YOUR CURRENT SSH SESSION OPEN and open a second one to verify
# before logging out. Run as root.   sudo bash scripts/firewall-setup.sh
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo)."; exit 1; }

SSH_PORT="${SSH_PORT:-22}"
SENSITIVE_PORTS=(3306 5432 6379 9000 9001 2358 9092 9093 9443 15672 5601)
CLOUDFLARE_ONLY="${CLOUDFLARE_ONLY:-0}"

echo "Plan:"
echo "  • ufw allow: $SSH_PORT/tcp (SSH), 80/tcp, 443/tcp; default deny incoming"
echo "  • DOCKER-USER: drop ports ${SENSITIVE_PORTS[*]} from non-local sources"
[ "$CLOUDFLARE_ONLY" = "1" ] && echo "  • DOCKER-USER: allow 80/443 ONLY from Cloudflare ranges (CLOUDFLARE_ONLY=1)"
read -r -p "Proceed? Keep your SSH session open. [y/N] " a; [ "$a" = "y" ] || { echo "Aborted."; exit 0; }

# ── ufw base ──────────────────────────────────────────────────────────
if command -v ufw >/dev/null 2>&1; then
  ufw allow "$SSH_PORT"/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw default deny incoming
  ufw default allow outgoing
  ufw --force enable
  echo "ufw configured."
else
  echo "ufw not installed; skipping (install with: apt-get install -y ufw)"
fi

# ── DOCKER-USER: block published infra ports from the outside ─────────
# Rules are inserted at the top of DOCKER-USER. Loopback + docker bridges are
# allowed first so inter-container and host-local traffic keeps working.
iptables -C DOCKER-USER -i lo -j RETURN 2>/dev/null || iptables -I DOCKER-USER -i lo -j RETURN
# Allow established/related so responses aren't dropped.
iptables -C DOCKER-USER -m state --state ESTABLISHED,RELATED -j RETURN 2>/dev/null \
  || iptables -I DOCKER-USER -m state --state ESTABLISHED,RELATED -j RETURN

for p in "${SENSITIVE_PORTS[@]}"; do
  # Allow from private/docker ranges; drop from everything else.
  iptables -C DOCKER-USER -p tcp --dport "$p" -s 172.16.0.0/12 -j RETURN 2>/dev/null \
    || iptables -A DOCKER-USER -p tcp --dport "$p" -s 172.16.0.0/12 -j RETURN
  iptables -C DOCKER-USER -p tcp --dport "$p" -s 10.0.0.0/8 -j RETURN 2>/dev/null \
    || iptables -A DOCKER-USER -p tcp --dport "$p" -s 10.0.0.0/8 -j RETURN
  iptables -C DOCKER-USER -p tcp --dport "$p" -j DROP 2>/dev/null \
    || iptables -A DOCKER-USER -p tcp --dport "$p" -j DROP
done
echo "DOCKER-USER infra-port rules applied."

# ── Optional: lock 80/443 to Cloudflare only ──────────────────────────
if [ "$CLOUDFLARE_ONLY" = "1" ]; then
  CF=$(curl -fsS https://www.cloudflare.com/ips-v4 2>/dev/null || true)
  [ -z "$CF" ] && { echo "Could not fetch Cloudflare IPs; aborting CF lock."; exit 1; }
  for net in $CF; do
    for port in 80 443; do
      iptables -C DOCKER-USER -p tcp --dport $port -s "$net" -j RETURN 2>/dev/null \
        || iptables -A DOCKER-USER -p tcp --dport $port -s "$net" -j RETURN
    done
  done
  for port in 80 443; do
    iptables -A DOCKER-USER -p tcp --dport $port -j DROP
  done
  echo "80/443 now restricted to Cloudflare ranges."
  echo "NOTE: also enable nginx real-IP (nginx/conf.d/cloudflare-realip.conf.example)."
fi

echo
echo "Done. Persist rules across reboot:  apt-get install -y iptables-persistent && netfilter-persistent save"
echo "Verify from a SECOND session before logging out of this one."
