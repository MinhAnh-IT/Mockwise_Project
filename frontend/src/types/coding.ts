/**
 * FE↔BE contract for the LeetCode-style live-coding flow.
 *
 * NONE of these endpoints exist on the backend yet — this file is the
 * source of truth for what `interview-service` must expose. See
 * `frontend/docs/coding-interview-contract.md` for the full spec and the
 * judge-service wiring the backend should reuse.
 *
 * The shapes mirror `question-bank · CodingQuestion` / `FunctionMeta` /
 * `StarterCode` / `TestCase` and the judge-service `TaskResultView`, so the
 * backend can map straight from the frozen `session_question.snapshot`
 * (which `QuestionPicker.buildSnapshot` already stores) without re-querying
 * question-bank.
 */

/** One of the four languages the system supports. Keys match BE `StarterCode`. */
export type CodingLanguage = 'java' | 'python' | 'cpp' | 'javascript';

export const CODING_LANGUAGES: readonly CodingLanguage[] = [
  'java',
  'python',
  'cpp',
  'javascript',
] as const;

/** Display label + Monaco language id for each supported language. */
export const LANGUAGE_META: Record<
  CodingLanguage,
  { label: string; monaco: string }
> = {
  java: { label: 'Java', monaco: 'java' },
  python: { label: 'Python 3', monaco: 'python' },
  cpp: { label: 'C++', monaco: 'cpp' },
  javascript: { label: 'JavaScript', monaco: 'javascript' },
};

/** Mirror of BE `FunctionMeta` (note: BE serialises `return` → `returnType`). */
export type FunctionMeta = {
  fn: string;
  params: { name: string; type: string }[];
  returnType: string;
  orderMatters: boolean;
  inPlace: boolean;
};

export type StarterCode = Record<CodingLanguage, string>;

/** A non-hidden sample test case the candidate may inspect / run against. */
export type SampleTestCase = {
  id: string;
  inputData: Record<string, unknown>;
  expectedOutput: Record<string, unknown>;
  /**
   * Human explanation for this example (LeetCode "Explanation"). Authored /
   * AI-generated; null when none. Only ever present on visible sample cases.
   */
  note?: string | null;
};

/**
 * Payload for one in-flight LIVE_CODING question. Backend serves this from
 * the frozen snapshot; `sampleTestCases` MUST exclude `is_hidden` cases.
 *
 * Proposed endpoint:
 *   GET /api/v1/interviews/{sid}/questions/{sqid}/coding
 */
export type CodingProblemView = {
  sessionQuestionId: string;
  sequence: number;
  title: string;
  /** Markdown. Rendered by ProblemPanel's lightweight renderer. */
  description: string;
  /**
   * LeetCode-style constraints — markdown, multi-line (one constraint per
   * row). Null when the question setter didn't provide any. Rendered as its
   * own list section, separate from the description.
   */
  constraints: string | null;
  optimalTimeComplexity: string | null;
  optimalSpaceComplexity: string | null;
  functionMeta: FunctionMeta;
  starterCode: StarterCode;
  sampleTestCases: SampleTestCase[];
};

export type RunCaseStatus = 'PASSED' | 'FAILED' | 'ERROR' | 'TIMEOUT';
export type RunStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export type RunCaseResult = {
  index: number;
  testCaseId: string;
  status: RunCaseStatus;
  stdout: string | null;
  expected: string | null;
  stderr: string | null;
  runtimeMs: number | null;
  memoryKb: number | null;
};

/**
 * Result of a "Run" against the visible sample cases. Maps to judge-service
 * `GET /api/v1/judge/status/{submissionId}` aggregated per case.
 *
 * Proposed endpoints:
 *   POST /api/v1/interviews/{sid}/questions/{sqid}/code/run        → { runId }
 *   GET  /api/v1/interviews/{sid}/questions/{sqid}/code/run/{runId} → RunResultView
 */
export type RunResultView = {
  status: RunStatus;
  /** Set when the submission failed to compile — show instead of cases. */
  compileError: string | null;
  cases: RunCaseResult[];
};

export type RunSubmitOutput = {
  runId: string;
};

export type RunCodeInput = {
  language: CodingLanguage;
  code: string;
};
