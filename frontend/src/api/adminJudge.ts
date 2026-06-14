/**
 * Admin Judge-monitoring client. Endpoints are served by judge-service under
 * /api/v1/judge/admin/** — routed by the gateway and gated to ROLE_ADMIN (any
 * path containing /admin/). All responses use the standard ApiResponse envelope
 * → unwrap.
 */
import { unwrap } from '@/api/client';
import type {
  DlqOverview,
  Judge0Health,
  JudgeJobDetail,
  JudgeJobPage,
  JudgeStats,
} from '@/types/judge';

const BASE = '/api/v1/judge/admin';

export const getJudgeStats = (window: string): Promise<JudgeStats> =>
  unwrap<JudgeStats>(`${BASE}/stats`, { query: { window } });

export const getJudgeJobs = (params: {
  status?: string;
  verdict?: string;
  page?: number;
  size?: number;
}): Promise<JudgeJobPage> =>
  unwrap<JudgeJobPage>(`${BASE}/jobs`, {
    query: {
      status: params.status,
      verdict: params.verdict,
      page: params.page,
      size: params.size,
    },
  });

export const getJudgeJobDetail = (submissionId: string): Promise<JudgeJobDetail> =>
  unwrap<JudgeJobDetail>(`${BASE}/jobs/${submissionId}`);

export const getJudgeDlq = (limit = 50): Promise<DlqOverview> =>
  unwrap<DlqOverview>(`${BASE}/dlq`, { query: { limit } });

export const getJudge0Health = (): Promise<Judge0Health> =>
  unwrap<Judge0Health>(`${BASE}/judge0`);
