/**
 * Admin feedback inbox client (user-profile-service). LIST returns the shared
 * `PageResponse` ({ items, page, size, totalElements, totalPages, ... }) inside
 * the ApiResponse envelope; stats returns the envelope directly → `unwrap`.
 */
import { request, unwrap } from '@/api/client';
import type { PageResult } from '@/types/profile';
import type {
  Feedback,
  FeedbackFilters,
  FeedbackStats,
  FeedbackStatusUpdatePayload,
} from '@/types/feedback';

const BASE = '/api/v1/admin/feedbacks';

export function listFeedback(
  filters: FeedbackFilters,
  page: number,
  size: number,
): Promise<PageResult<Feedback>> {
  return unwrap<PageResult<Feedback>>(BASE, {
    query: {
      status: filters.status,
      category: filters.category,
      rating: filters.rating,
      keyword: filters.keyword,
      page,
      size,
    },
  });
}

export const getFeedbackStats = (): Promise<FeedbackStats> =>
  unwrap<FeedbackStats>(`${BASE}/stats/overview`);

export const updateFeedbackStatus = (
  id: string,
  payload: FeedbackStatusUpdatePayload,
): Promise<Feedback> =>
  unwrap<Feedback>(`${BASE}/${id}/status`, { method: 'PATCH', body: payload });

export const deleteFeedback = (id: string): Promise<void> =>
  request<void>(`${BASE}/${id}`, { method: 'DELETE' });
