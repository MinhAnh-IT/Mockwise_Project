/**
 * Admin account moderation client (iam-service). Admins can block / unblock an
 * account but never edit a user's profile content — that stays the user's own
 * right. Both endpoints are ADMIN-gated at the gateway.
 */
import { unwrap } from '@/api/client';

const BASE = '/api/v1/iam/admin/users';

/** Subset of the IAM UserResponse we care about after a block/unblock. */
export type AdminUserState = {
  userId: string;
  email: string;
  role: string;
  isVerified: boolean;
  blocked: boolean;
};

export const blockUser = (userId: string): Promise<AdminUserState> =>
  unwrap<AdminUserState>(`${BASE}/${userId}/block`, { method: 'PATCH' });

export const unblockUser = (userId: string): Promise<AdminUserState> =>
  unwrap<AdminUserState>(`${BASE}/${userId}/unblock`, { method: 'PATCH' });
