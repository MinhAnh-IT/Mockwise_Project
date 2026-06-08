/**
 * Admin interview-oversight client (interview-service). Read-only: admins list
 * sessions and read aggregate stats — there is no detail or mutation endpoint
 * by design.
 *
 * Envelope notes (same as blueprint admin):
 * - LIST → ApiResponse(ApiListResponse) ({ totalCount, items }) → `unwrap`.
 * - stats → ApiResponse(stats object) → `unwrap`.
 */
import { unwrap } from '@/api/client';
import type {
  AdminSession,
  AdminSessionFilters,
  InterviewSessionStats,
} from '@/types/interview';

const BASE = '/api/v1/interviews/admin/sessions';

export type SessionPage = { totalCount: number | null; items: AdminSession[] };

export function listAdminSessions(
  filters: AdminSessionFilters,
  page: number,
  size: number,
): Promise<SessionPage> {
  return unwrap<SessionPage>(BASE, {
    query: {
      userId: filters.userId,
      status: filters.status,
      interviewType: filters.interviewType,
      targetRole: filters.targetRole,
      level: filters.level,
      from: filters.from,
      to: filters.to,
      page,
      size,
    },
  });
}

export const getSessionStats = (): Promise<InterviewSessionStats> =>
  unwrap<InterviewSessionStats>(`${BASE}/stats/overview`);
