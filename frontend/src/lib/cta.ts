import { useAuth } from '@/auth/useAuth';

// Destination for the primary "start practising" CTAs scattered across the
// landing page (hero, features, pricing, final CTA). Keeps the guest →
// register / user → practice / admin → console mapping in one place so every
// call-to-action stays consistent.
export function useStartHref(): string {
  const { status, role } = useAuth();
  if (status !== 'authenticated') return '/register';
  return role === 'ADMIN' ? '/admin' : '/practice';
}
