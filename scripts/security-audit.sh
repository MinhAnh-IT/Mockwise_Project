#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
# MockWise server security audit — READ ONLY. Changes nothing.
# Run on the VPS (as root or via sudo) before a public launch and re-run
# after firewall changes:
#     sudo bash scripts/security-audit.sh
#
# It answers the questions the repo can't: what is actually exposed to the
# internet, is the firewall on, and is the Judge0 code-execution sandbox
# locked down. Anything tagged [!] needs a look; [X] is a likely problem.
# ─────────────────────────────────────────────────────────────────────
set -uo pipefail

# Infra ports that must NEVER be reachable from the public internet.
SENSITIVE_PORTS=(3306 5432 6379 9000 9001 2358 9092 9093 8080 5601 9443 15672)
PUBLIC_OK_PORTS="22 80 443"

bold(){ printf '\n\033[1m%s\033[0m\n' "$1"; }
ok(){   printf '  \033[32m[OK]\033[0m %s\n' "$1"; }
warn(){ printf '  \033[33m[!]\033[0m %s\n' "$1"; }
bad(){  printf '  \033[31m[X]\033[0m %s\n' "$1"; }
info(){ printf '      %s\n' "$1"; }

bold "1. Host"
info "$(uname -srm 2>/dev/null);  uptime:$(uptime -p 2>/dev/null)"

# ── Firewall ──────────────────────────────────────────────────────────
bold "2. Firewall (ufw / iptables)"
if command -v ufw >/dev/null 2>&1; then
  if ufw status 2>/dev/null | grep -q "Status: active"; then
    ok "ufw is active"; ufw status verbose 2>/dev/null | sed 's/^/      /'
  else
    bad "ufw is INSTALLED but NOT active — host has no firewall"
  fi
else
  warn "ufw not installed (you may be using raw iptables / cloud firewall)"
fi
# Docker bypasses ufw via its own iptables chain — check DOCKER-USER.
if command -v iptables >/dev/null 2>&1; then
  rules=$(iptables -S DOCKER-USER 2>/dev/null | grep -vc '^-N\|RETURN$')
  if [ "${rules:-0}" -gt 0 ]; then ok "DOCKER-USER chain has custom rules (good — docker ports can be filtered)"
  else warn "DOCKER-USER chain has no custom rules → published docker ports BYPASS ufw and are world-open"; fi
fi

# ── What is actually listening on a public interface ──────────────────
bold "3. Ports listening on a PUBLIC interface (0.0.0.0 / ::)"
if command -v ss >/dev/null 2>&1; then
  LISTEN=$(ss -H -tlnp 2>/dev/null)
  echo "$LISTEN" | awk '{print $4}' | grep -E '0\.0\.0\.0:|\[::\]:|\*:' | \
    sed -E 's/.*:([0-9]+)$/\1/' | sort -un | while read -r p; do
      proc=$(echo "$LISTEN" | grep -E "[:.]$p " | grep -oE 'users:\(\("[^"]+' | head -1 | sed 's/users:(("//')
      if echo " $PUBLIC_OK_PORTS " | grep -q " $p "; then
        ok "port $p open to the world (expected) ${proc:+— $proc}"
      elif printf '%s\n' "${SENSITIVE_PORTS[@]}" | grep -qx "$p"; then
        bad "port $p (SENSITIVE) is listening on a PUBLIC interface ${proc:+— $proc}"
      else
        warn "port $p listening publicly ${proc:+— $proc} (confirm it should be)"
      fi
  done
else
  warn "ss not available; install iproute2"
fi

# ── Docker published ports ────────────────────────────────────────────
bold "4. Docker containers publishing ports to the host"
if command -v docker >/dev/null 2>&1; then
  docker ps --format '{{.Names}}\t{{.Ports}}' 2>/dev/null | while IFS=$'\t' read -r name ports; do
    [ -z "$ports" ] && continue
    if echo "$ports" | grep -qE '0\.0\.0\.0:|:::'; then
      hostp=$(echo "$ports" | grep -oE '0\.0\.0\.0:[0-9]+' | grep -oE '[0-9]+$' | tr '\n' ' ')
      flagged=""
      for p in $hostp; do printf '%s\n' "${SENSITIVE_PORTS[@]}" | grep -qx "$p" && flagged="$flagged $p"; done
      if [ -n "$flagged" ]; then bad "$name publishes SENSITIVE port(s):$flagged  → $ports"
      else info "$name → $ports"; fi
    else
      info "$name → $ports (bound to loopback only — good)"
    fi
  done
  echo
  info "TIP: bind infra to loopback in its compose, e.g. \"127.0.0.1:9000:9000\","
  info "     so MinIO/DB/Judge0 are NOT reachable from the internet."
else
  warn "docker CLI not found"
fi

# ── Judge0 sandbox (arbitrary user code runs here) ────────────────────
bold "5. Judge0 sandbox hardening"
J0=$(find /root /opt /srv -maxdepth 4 -name 'judge0.conf' 2>/dev/null | head -1)
if [ -n "$J0" ]; then
  info "found: $J0"
  netv=$(grep -E '^ENABLE_NETWORK=' "$J0" | cut -d= -f2)
  case "$netv" in
    false) ok "ENABLE_NETWORK=false (submissions can't reach the network)";;
    true)  bad "ENABLE_NETWORK=true → user code has internet access (SSRF / data exfil / abuse)";;
    *)     warn "ENABLE_NETWORK not set explicitly (default is true on many builds — set it to false)";;
  esac
  for k in MAX_CPU_TIME_LIMIT MAX_MEMORY_LIMIT MAX_PROCESSES_AND_OR_THREADS MAX_QUEUE_SIZE ENABLE_PER_PROCESS_AND_THREAD_TIME_LIMIT; do
    v=$(grep -E "^$k=" "$J0" | cut -d= -f2); [ -n "$v" ] && info "$k=$v" || warn "$k not set (using Judge0 default)"
  done
  grep -qE '^AUTHN_TOKEN=.+' "$J0" && ok "AUTHN_TOKEN set (Judge0 API requires a token)" || warn "AUTHN_TOKEN empty → anyone reaching :2358 can run code"
else
  warn "judge0.conf not found under /root /opt /srv — locate it and verify ENABLE_NETWORK=false + resource limits"
fi

# ── TLS cert ──────────────────────────────────────────────────────────
bold "6. TLS certificate"
CERT=/etc/letsencrypt/live/mockwise.io.vn/fullchain.pem
if [ -f "$CERT" ]; then
  end=$(openssl x509 -enddate -noout -in "$CERT" 2>/dev/null | cut -d= -f2)
  ok "cert present; expires: $end"
else warn "cert not found at $CERT"; fi

# ── Secrets hygiene ───────────────────────────────────────────────────
bold "7. .env file permissions"
for f in /opt/mockwise/.env /opt/mockwise/Final-Project/.env; do
  [ -f "$f" ] || continue
  perm=$(stat -c '%a %U' "$f" 2>/dev/null)
  if echo "$perm" | grep -qE '^6[04]0 |^400 '; then ok "$f ($perm)"; else bad "$f is too permissive ($perm) → chmod 600"; fi
done

# ── Brute-force daemon ────────────────────────────────────────────────
bold "8. fail2ban (SSH / abuse banning)"
if systemctl is-active --quiet fail2ban 2>/dev/null; then ok "fail2ban running"; else warn "fail2ban not active — recommended for SSH"; fi

# ── nginx config sanity ───────────────────────────────────────────────
bold "9. nginx config test"
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -q nginx; then
  cname=$(docker ps --format '{{.Names}}' | grep nginx | head -1)
  if docker exec "$cname" nginx -t >/tmp/ngx.$$ 2>&1; then ok "nginx -t passed ($cname)"; else bad "nginx -t FAILED:"; sed 's/^/      /' /tmp/ngx.$$; fi
  rm -f /tmp/ngx.$$
else warn "nginx container not running; can't test"; fi

bold "Audit complete."
echo "Review every [X] and [!] above. See docs/SECURITY-HARDENING.md for fixes."
