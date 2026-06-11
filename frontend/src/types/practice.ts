/**
 * FE↔BE contract for the LeetCode-style practice feature (practice-service,
 * mounted at /api/v1/practice). Distinct from the mock-interview "practice"
 * flow under /practice. Reuses the coding workspace value types from
 * {@link './coding'} (FunctionMeta / SampleTestCase / StarterCode).
 */
import type { FunctionMeta, SampleTestCase, StarterCode } from '@/types/coding';

/** A user's relationship to a problem. NONE = never attempted. */
export type ProblemStatus = 'NONE' | 'ATTEMPTED' | 'SOLVED';

export type SubmissionMode = 'RUN' | 'SUBMIT';

/** Submission lifecycle (not the verdict). */
export type SubmissionLifecycle = 'PENDING' | 'JUDGING' | 'DONE' | 'FAILED';

/** Raw judge aggregate verdict. AC = accepted. Null until DONE. */
export type Verdict = 'AC' | 'WA' | 'TLE' | 'MLE' | 'RE' | 'CE';

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

/** One row in the problem-list UI. */
export type ProblemSummary = {
  id: string;
  title: string;
  difficulty: string | null;
  tags: string[];
  optimalTimeComplexity: string | null;
  optimalSpaceComplexity: string | null;
  /** Global accept ratio — not yet computed by the BE (always null for now). */
  acceptanceRate: number | null;
  myStatus: ProblemStatus;
};

/** Problem detail for the workspace (hidden cases already stripped by the BE). */
export type PracticeProblemDetail = {
  id: string;
  title: string;
  description: string;
  constraints: string | null;
  optimalTimeComplexity: string | null;
  optimalSpaceComplexity: string | null;
  functionMeta: FunctionMeta;
  starterCode: Partial<StarterCode>;
  sampleTestCases: SampleTestCase[];
};

/** 202 response from run/submit — poll the submission by id. */
export type SubmissionCreated = {
  submissionId: string;
  status: SubmissionLifecycle;
};

export type SubmissionSummary = {
  id: string;
  problemId: string;
  problemTitle: string | null;
  language: string;
  mode: SubmissionMode;
  status: SubmissionLifecycle;
  verdict: string | null;
  passedCases: number;
  totalCases: number;
  runtimeMs: number | null;
  createdAt: string;
};

export type SubmissionCaseView = {
  orderIndex: number;
  testCaseId: string | null;
  status: string;
  runtimeMs: number | null;
  memoryKb: number | null;
  hidden: boolean;
  stdout: string | null;
  stderr: string | null;
};

export type SubmissionDetail = SubmissionSummary & {
  memoryKb: number | null;
  sourceCode: string;
  finishedAt: string | null;
  cases: SubmissionCaseView[];
};

export type UserStats = {
  solvedTotal: number;
  solvedByDifficulty: Record<string, number>;
  attemptedTotal: number;
  acceptanceRate: number | null;
  currentStreakDays: number;
  longestStreakDays: number;
};
