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
  /** The current user's relationship to this problem. */
  myStatus: ProblemStatus;
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
  /** Global catalog size (sum of totalByDifficulty) — denominator for the ring. */
  totalProblems: number;
  solvedByDifficulty: Record<string, number>;
  totalByDifficulty: Record<string, number>;
  /** Distinct solved problems per language (e.g. python/java). */
  solvedByLanguage: Record<string, number>;
  /** Distinct solved problems per question-bank tag. */
  solvedByTag: Record<string, number>;
  attemptedTotal: number;
  acceptanceRate: number | null;
  currentStreakDays: number;
  longestStreakDays: number;
};

export type LeaderboardWindow = 'ALL' | 'WEEK' | 'MONTH';

export type LeaderboardEntry = {
  rank: number;
  userId: string;
  fullName: string | null;
  solved: number;
  /** Difficulty-weighted score (Easy 1, Medium 3, Hard 5). */
  score: number;
  easy: number;
  medium: number;
  hard: number;
};

export type LeaderboardMe = {
  rank: number;
  solved: number;
  score: number;
  /** Rounded percentile, 1..100 (lower is better). */
  topPercent: number;
};

export type LeaderboardResponse = {
  window: string;
  totalParticipants: number;
  entries: LeaderboardEntry[];
  me: LeaderboardMe | null;
};

export type TrendingProblem = {
  problemId: string;
  title: string | null;
  difficulty: string | null;
  /** Distinct users who submitted (solved or not) in the window. */
  participants: number;
};

export type HardestProblem = {
  problemId: string;
  title: string | null;
  difficulty: string | null;
  /** Distinct users who attempted (≥1 graded SUBMIT). */
  attempters: number;
  /** Distinct users who ever reached AC. */
  solvers: number;
  /** solvers / attempters — fraction of attempters who solved it. */
  solveRate: number;
};

export type CommunityResponse = {
  trending: TrendingProblem[];
  hardest: HardestProblem[];
};

/** One problem's roll-up across the user's graded SUBMITs — LeetCode "progress" row. */
export type ProblemSubmissionGroup = {
  problemId: string;
  problemTitle: string | null;
  difficulty: string | null;
  solved: boolean;
  submissionCount: number;
  acceptedCount: number;
  bestRuntimeMs: number | null;
  firstSolvedAt: string | null;
  lastSubmittedAt: string;
};
