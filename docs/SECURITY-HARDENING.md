# MockWise — Security Hardening (pre-launch)

Status of the pre-launch security review. Items marked ✅ are already applied in
the repo; ⏳ are manual steps to run on the VPS before going public.

## What was already solid
- Only `nginx` is exposed; every service runs on internal docker networks.
- `.env` is gitignored and absent from git history; secrets are env-injected.
- Strong JWT HMAC secret + AI service key; `CORS_ORIGIN` is domain-locked in prod.
- BCrypt password hashing; 5-minute access tokens; gateway introspects every
  request, strips client-supplied `X-User-*` headers, blocks `/internal/`,
  enforces `/admin/`.
- TLS 1.2/1.3, HSTS, HTTP→HTTPS redirect; judge-service log rotation.

## ✅ Applied in this change

### Edge (`nginx/nginx.conf`, `nginx/conf.d/default.conf`)
- `server_tokens off` — stop leaking the nginx version.
- Slowloris/slow-POST mitigation: `client_header_timeout`, `client_body_timeout`,
  `send_timeout`.
- Security headers: `X-Frame-Options`, `X-Content-Type-Options`,
  `Referrer-Policy`, `Permissions-Policy` (mic/camera = self for interviews),
  `Content-Security-Policy: frame-ancestors 'self'`.
- Per-IP connection cap (`limit_conn conn_perip 20`) on `/api/` and `/`.
- **Login brute-force throttle**: the previously-unused `auth` zone (5 r/s) is now
  applied to `sign-in`, `register`, `forgot-password`, `verify-account`.
- **Swagger UI (`/docs`) and raw specs (`/api-spec/`) are no longer public** —
  `deny all` with a `# allow YOUR.ADMIN.IP` placeholder. Add your IP to use them.

### Auth / services
- `docker-compose.prod.yml`: removed the hardcoded `JWT_COOKIE_SECURE: "false"`
  that was overriding `.env` — auth cookies now carry `Secure` in prod.
- `AuthGatewayFilter`: removed the public Judge0 callback routes. Judge0 already
  posts results to judge-service over the internal network
  (`JUDGE_CALLBACK_BASE_URL=http://judge-service:8083`), so the public endpoint
  was needless attack surface (a guessed jobId could forge verdicts).

> After deploying, run `docker compose -f docker/docker-compose.prod.yml exec
> nginx nginx -t` then reload, and rebuild/redeploy the `api-gateway` image.

## ⏳ Manual steps on the VPS (highest impact first)

### 1. Put Cloudflare in front (best single DDoS control — free)
1. Add `mockwise.io.vn` to Cloudflare; switch the domain's nameservers.
2. Set the `A` record for `@`/`www` to `31.220.84.140` with the **orange cloud
   (proxied)** on.
3. SSL/TLS mode → **Full (strict)** (keeps the Let's Encrypt cert end-to-end).
4. Enable **Always Use HTTPS**, **Bot Fight Mode**, and a rate-limiting rule on
   `/api/v1/iam/auth/*`.
5. **Restore real client IPs at the origin** (mandatory): rename
   `nginx/conf.d/cloudflare-realip.conf.example` → `.conf` and reload nginx.
   Without it every request looks like it comes from Cloudflare and rate limits
   become useless.
6. Lock the origin so attackers can't bypass Cloudflare by hitting the IP:
   `sudo CLOUDFLARE_ONLY=1 bash scripts/firewall-setup.sh`.

### 2. Firewall + close infra ports
- Run `sudo bash scripts/security-audit.sh` first — it lists everything actually
  exposed. **MinIO `:9000` is currently reachable on the public IP** (nginx
  proxies to `31.220.84.140:9000`); confirm `:9001` console, DBs, Redis, Kafka
  and Judge0 `:2358` are *not* also world-open.
- Run `sudo bash scripts/firewall-setup.sh` to deny everything except 22/80/443
  and drop published infra ports from outside. Best practice: also change the
  infra composes to bind to `127.0.0.1:<port>:<port>` instead of `0.0.0.0`.

### 3. Judge0 sandbox (runs arbitrary user code — biggest app risk)
In `judge0.conf` on the VPS, verify:
- `ENABLE_NETWORK=false` (no internet from submissions → no SSRF/exfil/abuse),
- `MAX_CPU_TIME_LIMIT`, `MAX_MEMORY_LIMIT`, `MAX_PROCESSES_AND_OR_THREADS`,
  `MAX_QUEUE_SIZE` are set to sane caps,
- `AUTHN_TOKEN` is set so only judge-service can submit.
The audit script checks all of these.

### 4. Operational
- `chmod 600` the `.env` on the server.
- Install `fail2ban` for SSH.
- Persist firewall rules: `apt-get install -y iptables-persistent && netfilter-persistent save`.

## Recommended follow-ups (post-launch)
- App-level account lockout after N failed logins (defense in depth beyond the
  nginx throttle).
- Per-service container memory/CPU limits (measure real usage first to avoid
  OOM-killing at launch).
- Rotate and remove the `JWT_HMAC_SECRET` committed in
  `iam-service/src/main/resources/application-local.yml` (local profile only,
  but shouldn't live in git).
- Make the AI service API-key check fail-closed (reject when the key env is unset
  rather than allowing all).
- Verify `question-bank` `/questions/*/for-ai` and `/snapshot` (public GET) don't
  leak reference solutions / expected outputs to candidates.
