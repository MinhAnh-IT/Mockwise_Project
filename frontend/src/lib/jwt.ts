import type { UserRole } from '@/types/auth';

/**
 * Decode the `role` claim from a JWT access token without verifying the
 * signature. This is purely a UX affordance — it decides whether to *show*
 * the admin entry point. The real authorization boundary is the API gateway,
 * which re-introspects the token and 403s any `/admin/` path for non-admins.
 */
export function decodeJwtRole(token: string | null): UserRole | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    let b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    b64 += '='.repeat((4 - (b64.length % 4)) % 4);
    const json = JSON.parse(atob(b64)) as { role?: unknown };
    // The IAM `Role` enum is PascalCase (`Admin`/`User`), so the claim arrives
    // as e.g. "Admin" — normalise case-insensitively rather than matching the
    // uppercased FE convention exactly.
    const role =
      typeof json.role === 'string' ? json.role.toUpperCase() : null;
    return role === 'ADMIN' ? 'ADMIN' : role === 'USER' ? 'USER' : null;
  } catch {
    return null;
  }
}
