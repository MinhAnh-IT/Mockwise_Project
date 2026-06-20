import { unwrap } from '@/api/client';
import type { CodingLanguage } from '@/types/coding';
import type {
  CommunityResponse,
  LeaderboardResponse,
  LeaderboardWindow,
  PageResponse,
  PracticeProblemDetail,
  ProblemSubmissionGroup,
  ProblemSummary,
  SubmissionCreated,
  SubmissionDetail,
  SubmissionMode,
  SubmissionSummary,
  UserStats,
} from '@/types/practice';

/**
 * Practice client — LeetCode-style problem practice (practice-service).
 * All endpoints are JWT-gated and enveloped in ApiResponse, so every call
 * uses `unwrap`. The service browses question-bank's catalog and dispatches
 * Run/Submit to judge-service over Kafka; the FE polls the submission by id.
 */
const BASE = '/api/v1/practice';

export type ListProblemsParams = {
  difficulty?: string;
  tags?: string[];
  q?: string;
  page?: number;
  size?: number;
};

export function listProblems(
  params: ListProblemsParams = {},
): Promise<PageResponse<ProblemSummary>> {
  return unwrap(`${BASE}/problems`, {
    query: {
      difficulty: params.difficulty,
      // The BE binds repeated `tags` params; join is fine for the single-tag UI.
      tags: params.tags && params.tags.length ? params.tags.join(',') : undefined,
      q: params.q,
      page: params.page,
      size: params.size,
    },
  });
}

export function getProblem(id: string): Promise<PracticeProblemDetail> {
  return unwrap(`${BASE}/problems/${id}`);
}

export type RunSubmitInput = {
  language: CodingLanguage;
  code: string;
};

export function runProblem(
  id: string,
  input: RunSubmitInput,
): Promise<SubmissionCreated> {
  return unwrap(`${BASE}/problems/${id}/run`, { method: 'POST', body: input });
}

export function submitProblem(
  id: string,
  input: RunSubmitInput,
): Promise<SubmissionCreated> {
  return unwrap(`${BASE}/problems/${id}/submit`, { method: 'POST', body: input });
}

export function getSubmission(id: string): Promise<SubmissionDetail> {
  return unwrap(`${BASE}/submissions/${id}`);
}

export type ListSubmissionsParams = {
  problemId?: string;
  mode?: SubmissionMode;
  verdict?: string;
  page?: number;
  size?: number;
};

export function listSubmissions(
  params: ListSubmissionsParams = {},
): Promise<PageResponse<SubmissionSummary>> {
  return unwrap(`${BASE}/submissions`, {
    query: {
      problemId: params.problemId,
      mode: params.mode,
      verdict: params.verdict,
      page: params.page,
      size: params.size,
    },
  });
}

export function getStats(): Promise<UserStats> {
  return unwrap(`${BASE}/stats`);
}

/** My SUBMITs rolled up per problem (LeetCode "progress" view), newest activity first. */
export function listProblemGroups(): Promise<ProblemSubmissionGroup[]> {
  return unwrap(`${BASE}/submissions/grouped`);
}

/** Difficulty-weighted leaderboard for a time window, plus my own standing. */
export function getLeaderboard(
  window: LeaderboardWindow = 'ALL',
  limit = 50,
): Promise<LeaderboardResponse> {
  return unwrap(`${BASE}/leaderboard`, { query: { window, limit } });
}

/** Community insights: trending (this week) + hardest (lowest per-user solve rate) problems. */
export function getCommunity(): Promise<CommunityResponse> {
  return unwrap(`${BASE}/community`);
}
