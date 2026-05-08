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
import type { UserProfile } from '@/types/profile';

type Status = 'loading' | 'authenticated' | 'unauthenticated';

export type AuthContextValue = {
  status: Status;
  profile: UserProfile | null;
  signIn: (email: string, password: string) => Promise<void>;
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

  const accessTokenRef = useRef<string | null>(null);
  const refreshPromiseRef = useRef<Promise<string | null> | null>(null);

  const setAccessToken = useCallback((token: string | null) => {
    accessTokenRef.current = token;
  }, []);

  const refresh = useCallback(async (): Promise<string | null> => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current;

    refreshPromiseRef.current = (async () => {
      try {
        const data = await authApi.renewToken();
        accessTokenRef.current = data.accessToken;
        return data.accessToken;
      } catch {
        accessTokenRef.current = null;
        return null;
      } finally {
        refreshPromiseRef.current = null;
      }
    })();

    return refreshPromiseRef.current;
  }, []);

  const onAuthFailure = useCallback(() => {
    accessTokenRef.current = null;
    clearCachedProfile();
    setProfile(null);
    setStatus('unauthenticated');
  }, []);

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
    let cancelled = false;
    (async () => {
      const token = await refresh();
      if (cancelled) return;

      if (!token) {
        // No valid session on the server. Clear any stale cache and flip to
        // unauthenticated — this is the "reverse flash" case where the user
        // logged out from another device or the refresh cookie expired.
        clearCachedProfile();
        accessTokenRef.current = null;
        setProfile(null);
        setStatus('unauthenticated');
        return;
      }

      try {
        await loadProfile();
        if (!cancelled) setStatus('authenticated');
      } catch {
        if (!cancelled) {
          accessTokenRef.current = null;
          clearCachedProfile();
          setProfile(null);
          setStatus('unauthenticated');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh, loadProfile]);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const { accessToken } = await authApi.signIn({ email, password });
      accessTokenRef.current = accessToken;
      await loadProfile();
      setStatus('authenticated');
    },
    [loadProfile],
  );

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore — clear local state regardless
    }
    accessTokenRef.current = null;
    clearCachedProfile();
    setProfile(null);
    setStatus('unauthenticated');
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      profile,
      signIn,
      signOut,
      refreshProfile: loadProfile,
    }),
    [status, profile, signIn, signOut, loadProfile],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
