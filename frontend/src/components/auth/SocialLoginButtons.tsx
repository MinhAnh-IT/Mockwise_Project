import { Github } from 'lucide-react';
import {
  GITHUB_ENABLED,
  GOOGLE_ENABLED,
  startOAuth,
} from '@/auth/oauth';

type Props = {
  /** Where to land after a successful sign-in (defaults handled downstream). */
  returnTo?: string;
};

/**
 * "Continue with Google / GitHub" buttons. Renders nothing when neither
 * provider is configured (empty client id) so deployments without OAuth keys
 * degrade cleanly to email/password only.
 */
export default function SocialLoginButtons({ returnTo }: Props) {
  if (!GOOGLE_ENABLED && !GITHUB_ENABLED) return null;

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <span className="h-px flex-1 bg-outline-variant" />
        <span className="text-xs font-medium text-on-surface-variant">hoặc tiếp tục với</span>
        <span className="h-px flex-1 bg-outline-variant" />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {GOOGLE_ENABLED && (
          <button
            type="button"
            onClick={() => startOAuth('google', returnTo)}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-outline-variant bg-surface px-4 py-2.5 text-sm font-semibold text-on-surface transition-colors hover:bg-surface-container-low"
          >
            <GoogleIcon />
            Google
          </button>
        )}
        {GITHUB_ENABLED && (
          <button
            type="button"
            onClick={() => startOAuth('github', returnTo)}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-outline-variant bg-surface px-4 py-2.5 text-sm font-semibold text-on-surface transition-colors hover:bg-surface-container-low"
          >
            <Github className="h-4 w-4" />
            GitHub
          </button>
        )}
      </div>
    </div>
  );
}

function GoogleIcon() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.27-4.74 3.27-8.1Z"
      />
      <path
        fill="#34A853"
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.99.66-2.26 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84A11 11 0 0 0 12 23Z"
      />
      <path
        fill="#FBBC05"
        d="M5.84 14.1a6.6 6.6 0 0 1 0-4.2V7.06H2.18a11 11 0 0 0 0 9.88l3.66-2.84Z"
      />
      <path
        fill="#EA4335"
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1A11 11 0 0 0 2.18 7.06l3.66 2.84C6.71 7.3 9.14 5.38 12 5.38Z"
      />
    </svg>
  );
}
