import type { UserProfile } from '@/types/profile';

// Persist non-sensitive profile metadata so the app can render the user shell
// (avatar, name, nav) immediately on refresh — without the brief skeleton
// flash while /auth/token/renew + /profiles round-trip.
//
// SECURITY: never store the access token here. The access token stays
// in-memory; the refresh token lives in an HttpOnly cookie. localStorage is
// readable by any JS running on the page and is NOT safe for tokens.

const STORAGE_KEY = 'mockwise.auth.profile.v1';

export function readCachedProfile(): UserProfile | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<UserProfile>;
    if (!parsed || typeof parsed !== 'object' || !parsed.userId || !parsed.fullName) {
      return null;
    }
    return parsed as UserProfile;
  } catch {
    return null;
  }
}

export function writeCachedProfile(profile: UserProfile): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
  } catch {
    // localStorage may be disabled (private mode, full quota); cache is best-effort.
  }
}

export function clearCachedProfile(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // best-effort
  }
}
