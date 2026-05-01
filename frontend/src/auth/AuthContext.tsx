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
  const [status, setStatus] = useState<Status>('loading');
  const [profile, setProfile] = useState<UserProfile | null>(null);

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
    setProfile(null);
    setStatus('unauthenticated');
  }, []);

  useEffect(() => {
    configureApiClient({
      getAccessToken: () => accessTokenRef.current,
      setAccessToken,
      refresh,
      onAuthFailure,
    });
  }, [setAccessToken, refresh, onAuthFailure]);

  const loadProfile = useCallback(async () => {
    const data = await getMyProfile();
    setProfile(data);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = await refresh();
      if (cancelled) return;
      if (!token) {
        setStatus('unauthenticated');
        return;
      }
      try {
        await loadProfile();
        if (!cancelled) setStatus('authenticated');
      } catch {
        if (!cancelled) {
          accessTokenRef.current = null;
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
