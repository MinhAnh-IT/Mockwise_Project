import { request, unwrap } from '@/api/client';
import type {
  CodingProblemView,
  RunCaseResult,
  RunCaseStatus,
  RunResultView,
  RunStatus,
  RunCodeInput,
  RunSubmitOutput,
  SampleTestCase,
} from '@/types/coding';

/**
 * Live-coding client.
 *
 * - The PROBLEM is served by interview-service (owner-gated, hidden test
 *   cases stripped): GET /api/v1/interviews/{sid}/questions/{sqid}/coding.
 * - "Run" talks to judge-service DIRECTLY (no interview-service hop) — it's
 *   throwaway, never persisted and must not affect scoring. interview-service
 *   only gets involved on Submit (the normal CODE answer flow), which is what
 *   actually runs the full hidden+shown suite and scores the answer.
 *
 * judge-service responses are NOT wrapped in the ApiResponse envelope, so
 * the Run calls use `request` (raw JSON) while the problem fetch uses
 * `unwrap` (enveloped).
 */

const INTERVIEWS = '/api/v1/interviews';
const JUDGE = '/api/v1/judge';

/** GET the LIVE_CODING problem for one in-flight session question. */
export function getCodingProblem(
  sessionId: string,
  sessionQuestionId: string,
): Promise<CodingProblemView> {
  return unwrap(`${INTERVIEWS}/${sessionId}/questions/${sessionQuestionId}/coding`);
}

type JudgeSubmitResponse = { submissionId: string; message?: string };

type JudgeTaskResult = {
  testCaseId: string;
  orderIndex: number;
  status: string; // AC | WA | TLE | MLE | RE | CE | PENDING
  stdout: string | null;
  stderr: string | null;
  runtimeMs: number | null;
  memoryKb: number | null;
};

type JudgeStatusResponse = {
  jobId: string;
  submissionId: string;
  status: string; // PENDING | RUNNING | DONE | FAILED
  verdict: string | null;
  doneCases: number;
  totalCases: number;
  results: JudgeTaskResult[];
};

/**
 * Kick off a Run against the visible sample cases by calling judge-service
 * directly. The judge needs `functionMeta` + the test cases, which the
 * already-loaded problem carries (sample cases include their expected
 * output). Note: judge `FunctionMeta` expects the return type under the
 * JSON key `return`, not `returnType`.
 */
export function runCode(
  problem: CodingProblemView,
  input: RunCodeInput,
  /**
   * Test cases to run against. Defaults to the problem's visible sample
   * cases (the candidate flow). The admin "Kiểm tra" flow passes the full
   * suite here — hidden cases included — to validate a question end-to-end.
   */
  cases: SampleTestCase[] = problem.sampleTestCases,
  /**
   * Mark this as a throwaway run (admin validation): judge-service skips the
   * verdict publish and purges the job rows shortly after. Defaults to false
   * for the candidate Run flow.
   */
  ephemeral = false,
): Promise<RunSubmitOutput> {
  const fm = problem.functionMeta;
  const submission = {
    language: input.language,
    code: input.code,
    ephemeral,
    functionMeta: {
      fn: fm.fn,
      params: fm.params,
      return: fm.returnType,
      orderMatters: fm.orderMatters,
      inPlace: fm.inPlace,
    },
    testCases: cases.map((tc) => ({
      id: tc.id,
      inputData: tc.inputData,
      expectedOutput: tc.expectedOutput,
    })),
  };
  return request<JudgeSubmitResponse>(`${JUDGE}/submit`, {
    method: 'POST',
    body: submission,
  }).then((r) => ({ runId: r.submissionId }));
}

/** Poll a Run until `status` is DONE or FAILED. */
export function getRunResult(
  runId: string,
  problem: CodingProblemView,
  /** Same suite passed to {@link runCode}; used to map each case's expected. */
  cases: SampleTestCase[] = problem.sampleTestCases,
): Promise<RunResultView> {
  return request<JudgeStatusResponse>(`${JUDGE}/status/${runId}`).then((j) =>
    mapJudgeStatus(j, cases),
  );
}

// ── judge-service → RunResultView mapping ──────────────────────────────────

function mapJudgeStatus(
  j: JudgeStatusResponse,
  samples: SampleTestCase[],
): RunResultView {
  const expectedById = new Map<string, string>();
  for (const tc of samples) {
    expectedById.set(tc.id, stringifyExpected(tc.expectedOutput));
  }

  const cases: RunCaseResult[] = (j.results ?? []).map((t) => ({
    index: t.orderIndex,
    testCaseId: t.testCaseId,
    status: mapCaseStatus(t.status),
    stdout: t.stdout,
    expected: expectedById.get(t.testCaseId) ?? null,
    stderr: t.stderr,
    runtimeMs: t.runtimeMs,
    memoryKb: t.memoryKb,
  }));

  // A compile error makes per-case output meaningless — surface the
  // compiler message (judge puts it on the failing case's stderr) instead.
  const ce = (j.results ?? []).find(
    (t) => (t.status ?? '').toUpperCase() === 'CE',
  );

  return {
    status: mapJobStatus(j.status),
    compileError: ce ? ce.stderr ?? 'Compilation error' : null,
    cases,
  };
}

function mapJobStatus(s: string): RunStatus {
  switch ((s ?? '').toUpperCase()) {
    case 'PENDING':
      return 'PENDING';
    case 'RUNNING':
      return 'RUNNING';
    case 'DONE':
      return 'DONE';
    default:
      return 'FAILED';
  }
}

function mapCaseStatus(s: string): RunCaseStatus {
  switch ((s ?? '').toUpperCase()) {
    case 'AC':
      return 'PASSED';
    case 'WA':
      return 'FAILED';
    case 'TLE':
      return 'TIMEOUT';
    default:
      // RE | MLE | CE | PENDING | unknown
      return 'ERROR';
  }
}

/**
 * Mirrors the demo fallback: show the single expected value when the
 * expectedOutput map has one key, else the whole object.
 */
function stringifyExpected(expectedOutput: Record<string, unknown>): string {
  const values = Object.values(expectedOutput ?? {});
  if (values.length === 1) {
    return JSON.stringify(values[0]);
  }
  return JSON.stringify(expectedOutput ?? null);
}
