/**
 * Public feedback submission (user-profile-service). The endpoint is reachable
 * without auth so landing-page visitors can send feedback — `auth: false` skips
 * the token cold-start refresh that authed calls do.
 */
import { unwrap } from '@/api/client';
import type { Feedback, FeedbackCreatePayload } from '@/types/feedback';

export const submitFeedback = (payload: FeedbackCreatePayload): Promise<Feedback> =>
  unwrap<Feedback>('/api/v1/feedbacks', {
    method: 'POST',
    body: payload,
    auth: false,
  });
