import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import * as authApi from '@/api/auth';
import { configureApiClient } from '@/api/client';
import { getMyProfile } from '@/api/profile';
import {
  clearCachedProfile,
  readCachedProfile,
  writeCachedProfile,
} from '@/auth/storage';
import { decodeJwtRole } from '@/lib/jwt';
import type { UserRole } from '@/types/auth';
import type { UserProfile } from '@/types/profile';

type Status = 'loading' | 'authenticated' | 'unauthenticated';

export type AuthContextValue = {
  status: Status;
  profile: UserProfile | null;
  /** Decoded from the JWT `role` claim. Null until the first token resolves. */
  role: UserRole | null;
  signIn: (email: string, password: string) => Promise<void>;
  /**
   * Adopt a session minted by the social-login exchange. When the account still
   * needs to complete its profile (`profileCompleted=false`) we skip loading the
   * profile (it doesn't exist yet) and let the caller route to the completion
   * page; status is still flipped to authenticated since the JWT is valid.
   */
  signInWithOAuth: (accessToken: string, profileCompleted: boolean) => Promise<void>;
  signOut: () => Promise<void>;
  refreshProfile: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

type Props = { children: ReactNode };

export function AuthProvider({ children }: Props) {
  // If we have a cached profile from a prior session, optimistically render
  // the authenticated UI immediately and verify with the server in the
  // background. This avoids the ~500ms skeleton flash on every refresh.
  const cachedProfile = readCachedProfile();

  const [status, setStatus] = useState<Status>(
    cachedProfile ? 'authenticated' : 'loading',
  );
  const [profile, setProfile] = useState<UserProfile | null>(cachedProfile);
  const [role, setRole] = useState<UserRole | null>(null);

  const accessTokenRef = useRef<string | null>(null);
  const refreshPromiseRef = useRef<Promise<string | null> | null>(null);

  // Single choke-point for "the access token changed": keep the ref (read by
  // the API client) and the decoded role state in lock-step.
  const applyToken = useCallback((token: string | null) => {
    accessTokenRef.current = token;
    setRole(decodeJwtRole(token));
  }, []);

  const setAccessToken = useCallback(
    (token: string | null) => applyToken(token),
    [applyToken],
  );

  const refresh = useCallback(async (): Promise<string | null> => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current;

    refreshPromiseRef.current = (async () => {
      try {
        const data = await authApi.renewToken();
        applyToken(data.accessToken);
        return data.accessToken;
      } catch {
        applyToken(null);
        return null;
      } finally {
        refreshPromiseRef.current = null;
      }
    })();

    return refreshPromiseRef.current;
  }, [applyToken]);

  const onAuthFailure = useCallback(() => {
    applyToken(null);
    clearCachedProfile();
    setProfile(null);
    setStatus('unauthenticated');
  }, [applyToken]);

  // Wire the API client during render rather than in a useEffect. React runs
  // child useEffects BEFORE the parent's, so a child like HistoryPage that
  // fires getSession on mount would otherwise read `accessor === null` and
  // dispatch the request without an Authorization header. The accessor is a
  // small, idempotent object — re-assigning it on every render is cheap and
  // keeps it available throughout the entire render-commit-effects cycle.
  configureApiClient({
    getAccessToken: () => accessTokenRef.current,
    setAccessToken,
    refresh,
    onAuthFailure,
  });

  const loadProfile = useCallback(async () => {
    const data = await getMyProfile();
    setProfile(data);
    writeCachedProfile(data);
  }, []);

  useEffect(() => {
    // The OAuth callback page is a full page load that establishes the session
    // itself (code→token exchange). Skip the mount refresh there so its null
    // result (no refresh cookie yet) can't race with and clobber the session
    // the callback is about to set.
    if (window.location.pathname === '/auth/callback') {
      return;
    }

    let cancelled = false;
    (async () => {
      const token = await refresh();
      if (cancelled) return;

      if (!token) {
        // No valid session on the server. Clear any stale cache and flip to
        // unauthenticated — this is the "reverse flash" case where the user
        // logged out from another device or the refresh cookie expired.
        clearCachedProfile();
        applyToken(null);
        setProfile(null);
        setStatus('unauthenticated');
        return;
      }

      try {
        await loadProfile();
        if (!cancelled) setStatus('authenticated');
      } catch {
        if (!cancelled) {
          applyToken(null);
          clearCachedProfile();
          setProfile(null);
          setStatus('unauthenticated');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh, loadProfile, applyToken]);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const { accessToken } = await authApi.signIn({ email, password });
      applyToken(accessToken);
      await loadProfile();
      setStatus('authenticated');
    },
    [loadProfile, applyToken],
  );

  const signInWithOAuth = useCallback(
    async (accessToken: string, profileCompleted: boolean) => {
      applyToken(accessToken);
      if (profileCompleted) {
        await loadProfile();
      }
      setStatus('authenticated');
    },
    [applyToken, loadProfile],
  );

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore — clear local state regardless
    }
    applyToken(null);
    clearCachedProfile();
    setProfile(null);
    setStatus('unauthenticated');
  }, [applyToken]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      profile,
      role,
      signIn,
      signInWithOAuth,
      signOut,
      refreshProfile: loadProfile,
    }),
    [status, profile, role, signIn, signInWithOAuth, signOut, loadProfile],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
