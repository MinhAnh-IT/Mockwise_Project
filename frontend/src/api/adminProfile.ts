/**
 * Admin user-profile client (user-profile-service). Read-only: admins list and
 * inspect profiles + see the count, but do NOT edit profile content. Account
 * moderation (block/unblock) lives in `api/adminUsers.ts` (iam-service).
 *
 * Envelope notes:
 * - LIST is wrapped in the ApiResponse envelope; its `data` is a `PageResponse`
 *   ({ items, page, size, totalElements, totalPages, numberOfElements }) → `unwrap`.
 * - stats returns the ApiResponse envelope (data = { totalProfiles }) → `unwrap`.
 */
import { unwrap } from '@/api/client';
import type {
  AdminProfileFilters,
  AdminUserProfile,
  PageResult,
  ProfileStats,
} from '@/types/profile';

const BASE = '/api/v1/admin/profiles';

export function listAdminProfiles(
  filters: AdminProfileFilters,
  page: number,
  size: number,
): Promise<PageResult<AdminUserProfile>> {
  return unwrap<PageResult<AdminUserProfile>>(BASE, {
    query: {
      trackId: filters.trackId,
      levelId: filters.levelId,
      keyword: filters.keyword,
      page,
      size,
    },
  });
}

export const getProfileStats = (): Promise<ProfileStats> =>
  unwrap<ProfileStats>(`${BASE}/stats/overview`);
