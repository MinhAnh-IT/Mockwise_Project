import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { exchangeOAuthCode } from '@/api/auth';
import { oauthRedirectUri, verifyOAuthCallback } from '@/auth/oauth';
import { useAuth } from '@/auth/useAuth';
import AuthLayout from '@/components/auth/AuthLayout';
import FormError from '@/components/auth/FormError';
import { decodeJwtRole } from '@/lib/jwt';

/**
 * Landing page for the provider redirect (/auth/callback). Validates the CSRF
 * state, exchanges the code for our session, then routes the user to the app
 * or to profile completion. On error, shows a message with a way back to login.
 */
export default function OAuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { signInWithOAuth } = useAuth();
  const [error, setError] = useState<string | null>(null);

  // Guard against React 18 StrictMode double-invoking the effect (and thus the
  // one-time, single-use code exchange) in development.
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    (async () => {
      const providerError = params.get('error');
      const code = params.get('code');
      const state = params.get('state');

      if (providerError) {
        setError('Đăng nhập bị huỷ hoặc bị từ chối. Vui lòng thử lại.');
        return;
      }
      if (!code) {
        setError('Thiếu mã xác thực từ nhà cung cấp. Vui lòng thử lại.');
        return;
      }

      try {
        const { provider, returnTo } = verifyOAuthCallback(state);
        const { accessToken, profileCompleted } = await exchangeOAuthCode(
          provider,
          code,
          oauthRedirectUri(),
        );
        await signInWithOAuth(accessToken, profileCompleted);
        if (!profileCompleted) {
          navigate('/complete-profile', { replace: true });
        } else {
          // Admins land on the admin console; regular users on the home page.
          // A `returnTo` carried through the OAuth state always wins.
          const role = decodeJwtRole(accessToken);
          navigate(returnTo ?? (role === 'ADMIN' ? '/admin' : '/'), { replace: true });
        }
      } catch (err) {
        setError(
          err instanceof Error ? err.message : 'Không thể hoàn tất đăng nhập. Vui lòng thử lại.',
        );
      }
    })();
  }, [params, navigate, signInWithOAuth]);

  if (error) {
    return (
      <AuthLayout
        title="Đăng nhập thất bại"
        subtitle="Đã có sự cố khi đăng nhập bằng mạng xã hội"
        footer={
          <Link to="/login" className="font-semibold text-secondary hover:underline">
            Quay lại đăng nhập
          </Link>
        }
      >
        <FormError message={error} />
      </AuthLayout>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center text-on-surface-variant">
      <div className="flex flex-col items-center gap-3">
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-outline-variant border-t-secondary" />
        <p className="text-sm">Đang hoàn tất đăng nhập…</p>
      </div>
    </div>
  );
}
