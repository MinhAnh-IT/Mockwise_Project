# Social Login (Google & GitHub) — Setup & Operations

Adds "Continue with Google / GitHub" alongside the existing email/password +
OTP login. Flow: **authorization-code + backend exchange** — the SPA redirects
to the provider, the provider redirects back to `/auth/callback?code=...`, the
SPA posts the code to iam-service which exchanges it server-side (client secret
never leaves the backend), find-or-creates the user, and issues our normal JWT
access token + refresh cookie. First-time OAuth users are routed to
`/complete-profile` to fill in track/level/city.

## 1. Create the OAuth apps

Redirect/callback URI for **both** providers must be the SPA origin +
`/auth/callback`:

- Local: `http://localhost:3000/auth/callback`
- Prod:  `https://<your-domain>/auth/callback`

**Google** — Google Cloud Console → APIs & Services → Credentials → *OAuth client
ID* (type: Web application). Add the redirect URI above. Scopes used:
`openid email profile`.

**GitHub** — Settings → Developer settings → *OAuth Apps* → New OAuth App. Set
the *Authorization callback URL* to the URI above. Scopes used:
`read:user user:email` (the email scope is required — we read the primary
**verified** email from `/user/emails`).

## 2. Configuration

**Backend** (iam-service) — env vars (added to the deploy `--env-file`):

```
OAUTH_GOOGLE_CLIENT_ID=...
OAUTH_GOOGLE_CLIENT_SECRET=...
OAUTH_GITHUB_CLIENT_ID=...
OAUTH_GITHUB_CLIENT_SECRET=...
```

Bound via `app.oauth.*` in `application.yml`. For local runs, fill the block in
`application-local.yml`.

**Frontend** — public client IDs only (`.env`):

```
VITE_GOOGLE_CLIENT_ID=...
VITE_GITHUB_CLIENT_ID=...
```

An empty value hides that provider's button, so deployments without keys
degrade cleanly to email/password.

## 3. Database migration (REQUIRED before first OAuth login)

iam-service runs `ddl-auto: update`, which **adds** the three new columns
automatically (`auth_provider`, `provider_id`, `profile_completed`) but does
**not** relax the existing `hash_pass NOT NULL` constraint. OAuth accounts have
no password, so run this once per environment (it cannot be done by ddl-auto):

```sql
ALTER TABLE users MODIFY hash_pass VARCHAR(255) NULL;
```

Existing rows get `profile_completed = true` (column default) and
`auth_provider = 'LOCAL'`, so password users are unaffected.

## 4. Account-linking policy (by verified email)

The provider email must be **verified** or the request is rejected
(`OAUTH_EMAIL_NOT_VERIFIED`). Then:

| Existing account            | Behaviour |
|-----------------------------|-----------|
| none                        | Create passwordless account, verified, `profile_completed=false`. |
| exists, **verified** (TH1)  | Auto-link the provider; existing password kept → can sign in either way. |
| exists, **unverified** (TH2)| Take over for the real owner: mark verified, **null the password**, bump `tokenVersion` (defeats pre-account-hijacking). User must "forgot password" to use a password again. |

Password sign-in is gated on `hash_pass != null` (not on `auth_provider`), so a
local user who links Google keeps password login.

## 5. Key endpoints

- `POST /api/v1/iam/auth/oauth/{provider}/exchange` — public; `{ code, redirectUri }` → `{ accessToken, profileCompleted }`. `provider` ∈ `google|github`.
- `POST /api/v1/iam/users/me/profile` — authed; first-time profile completion (`X-User-Id` from gateway).

Gateway: `/api/v1/iam/auth/oauth/**` is whitelisted in `AuthGatewayFilter`.

## 6. Verifying end-to-end

1. After the migration + config, restart iam-service **and** the api-gateway
   (gateway caches backend container IPs — see deploy notes).
2. Google new user → button → consent → `/complete-profile` → fill form → app;
   DB row has `auth_provider=GOOGLE, profile_completed=1`.
3. Sign in again with Google → straight to the app (no profile prompt).
4. GitHub new user → same, using the primary verified email.
5. TH1: register `x@mail` with a password & verify, then Google with `x@mail`
   → links, no duplicate row; both password and Google work.
6. Blocked user via OAuth → rejected.
