import { GITHUB_CLIENT_ID, GOOGLE_CLIENT_ID } from '@/lib/env';
import type { OAuthProviderName } from '@/types/auth';

// Social-login handshake helpers. The provider redirects back to
// /auth/callback with ?code & ?state; we round-trip an opaque `state` through
// sessionStorage to defeat CSRF and to remember where to land afterwards.

const STATE_KEY = 'mockwise.oauth.state';
const RETURN_KEY = 'mockwise.oauth.return';

export const GOOGLE_ENABLED = GOOGLE_CLIENT_ID !== '';
export const GITHUB_ENABLED = GITHUB_CLIENT_ID !== '';

/** Must exactly match the redirect URI registered with each provider. */
export function oauthRedirectUri(): string {
  return `${window.location.origin}/auth/callback`;
}

/** Kick off the provider redirect. `returnTo` is where to go on success. */
export function startOAuth(provider: OAuthProviderName, returnTo?: string): void {
  const state = `${provider}.${crypto.randomUUID()}`;
  sessionStorage.setItem(STATE_KEY, state);
  if (returnTo) sessionStorage.setItem(RETURN_KEY, returnTo);

  const redirectUri = oauthRedirectUri();
  let url: string;
  if (provider === 'google') {
    url =
      'https://accounts.google.com/o/oauth2/v2/auth?' +
      new URLSearchParams({
        client_id: GOOGLE_CLIENT_ID,
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'openid email profile',
        state,
        prompt: 'select_account',
      }).toString();
  } else {
    url =
      'https://github.com/login/oauth/authorize?' +
      new URLSearchParams({
        client_id: GITHUB_CLIENT_ID,
        redirect_uri: redirectUri,
        scope: 'read:user user:email',
        state,
      }).toString();
  }
  window.location.assign(url);
}

/**
 * Validate the `state` returned by the provider against the one we stored, and
 * recover the provider + post-login destination. Clears the stored values
 * (single-use). Throws if the state is missing or tampered with.
 */
export function verifyOAuthCallback(urlState: string | null): {
  provider: OAuthProviderName;
  returnTo: string | null;
} {
  const stored = sessionStorage.getItem(STATE_KEY);
  const returnTo = sessionStorage.getItem(RETURN_KEY);
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(RETURN_KEY);

  if (!urlState || !stored || urlState !== stored) {
    throw new Error('Phiên đăng nhập không hợp lệ. Vui lòng thử lại.');
  }
  const provider = stored.split('.')[0];
  if (provider !== 'google' && provider !== 'github') {
    throw new Error('Nhà cung cấp đăng nhập không hỗ trợ.');
  }
  return { provider, returnTo };
}
