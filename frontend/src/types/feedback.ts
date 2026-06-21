/**
 * User feedback types. Mirrors the backend (user-profile-service):
 * entity/Feedback.java + dto/{request,response}. Submission is public; listing
 * and triage are admin-only.
 */

export type FeedbackCategory = 'BUG' | 'FEATURE' | 'GENERAL';
export type FeedbackStatus = 'NEW' | 'REVIEWED' | 'RESOLVED';

export const FEEDBACK_CATEGORY_LABEL: Record<FeedbackCategory, string> = {
  BUG: 'Báo lỗi',
  FEATURE: 'Đề xuất tính năng',
  GENERAL: 'Góp ý chung',
};

export const FEEDBACK_STATUS_LABEL: Record<FeedbackStatus, string> = {
  NEW: 'Mới',
  REVIEWED: 'Đang xử lý',
  RESOLVED: 'Đã xử lý',
};

export type Feedback = {
  id: string;
  /** Author when the sender was logged in; null for anonymous submissions. */
  userId: string | null;
  rating: number;
  category: FeedbackCategory;
  content: string;
  contactEmail: string | null;
  status: FeedbackStatus;
  adminNote: string | null;
  createdAt: string;
  reviewedAt: string | null;
};

export type FeedbackCreatePayload = {
  rating: number;
  category: FeedbackCategory;
  content: string;
  contactEmail?: string;
};

export type FeedbackFilters = {
  status?: FeedbackStatus;
  category?: FeedbackCategory;
  rating?: number;
  keyword?: string;
};

export type FeedbackStatusUpdatePayload = {
  status: FeedbackStatus;
  adminNote?: string;
};

export type FeedbackStats = {
  total: number;
  newCount: number;
  reviewedCount: number;
  resolvedCount: number;
  bugCount: number;
  featureCount: number;
  generalCount: number;
  avgRating: number;
};
