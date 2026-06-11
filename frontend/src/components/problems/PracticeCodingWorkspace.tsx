import { useCallback, useEffect, useRef, useState } from 'react';
import {
  CheckCircle2,
  ChevronLeft,
  Loader2,
  Moon,
  Play,
  RotateCcw,
  Send,
  Sun,
  XCircle,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  getProblem,
  getSubmission,
  runProblem,
  submitProblem,
} from '@/api/practice';
import {
  CODING_LANGUAGES,
  LANGUAGE_META,
  type CodingLanguage,
  type RunCaseResult,
  type RunCaseStatus,
  type RunResultView,
  type RunStatus,
} from '@/types/coding';
import type {
  PracticeProblemDetail,
  SubmissionDetail,
} from '@/types/practice';
import CodeEditor from '@/components/practice/coding/CodeEditor';
import Markdown from '@/components/practice/coding/Markdown';
import TestcasePanel from '@/components/practice/coding/TestcasePanel';
import {
  CW_THEME_STORAGE_KEY,
  cwTokens,
  difficultyStyle,
  readStoredAppearance,
  type CwAppearance,
} from '@/components/practice/coding/theme';

type Props = {
  problemId: string;
  /** Optional, passed from the list via router state — purely cosmetic. */
  difficulty?: string | null;
  onExit: () => void;
  /** Notified when a SUBMIT reaches a terminal verdict (to refresh status). */
  onSubmitted?: (verdict: string | null) => void;
};

const POLL_MS = 1200;
const POLL_MAX = 60; // ~72s ceiling

const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: 'Dễ',
  MEDIUM: 'Trung bình',
  HARD: 'Khó',
};

/**
 * LeetCode-style workspace for the practice feature. Reuses the interview
 * coding panels (CodeEditor / TestcasePanel / Markdown / theme) but drives
 * Run/Submit against practice-service (which dispatches to judge over Kafka
 * and persists history) and polls the submission by id.
 */
export default function PracticeCodingWorkspace({
  problemId,
  difficulty,
  onExit,
  onSubmitted,
}: Props) {
  const [problem, setProblem] = useState<PracticeProblemDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [language, setLanguage] = useState<CodingLanguage>('java');
  const [codeByLang, setCodeByLang] = useState<Record<CodingLanguage, string>>({
    java: '',
    python: '',
    cpp: '',
    javascript: '',
  });

  const [busy, setBusy] = useState(false); // a run/submit is in flight
  const [runResult, setRunResult] = useState<RunResultView | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // Banner for the last SUBMIT verdict (run results show in the console).
  const [verdict, setVerdict] = useState<{ verdict: string | null; passed: number; total: number } | null>(null);

  const [leftPct, setLeftPct] = useState(44);
  const [consolePct, setConsolePct] = useState(40);

  const [appearance, setAppearance] = useState<CwAppearance>(readStoredAppearance);
  const dark = appearance === 'dark';
  const t = cwTokens(dark);
  const cancelPoll = useRef(false);

  const toggleAppearance = useCallback(() => {
    setAppearance((prev) => {
      const next = prev === 'dark' ? 'light' : 'dark';
      try {
        window.localStorage.setItem(CW_THEME_STORAGE_KEY, next);
      } catch {
        /* storage disabled — non-fatal */
      }
      return next;
    });
  }, []);

  // ── Load problem ──────────────────────────────────────────────────────
  useEffect(() => {
    let cancelled = false;
    setProblem(null);
    setLoadError(null);
    setRunResult(null);
    setVerdict(null);
    getProblem(problemId)
      .then((p) => {
        if (cancelled) return;
        setProblem(p);
        setCodeByLang({
          java: p.starterCode.java ?? '',
          python: p.starterCode.python ?? '',
          cpp: p.starterCode.cpp ?? '',
          javascript: p.starterCode.javascript ?? '',
        });
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(
          err instanceof ApiError ? err.message : 'Không tải được đề bài.',
        );
      });
    return () => {
      cancelled = true;
    };
  }, [problemId]);

  // Stop any in-flight poll loop on unmount.
  useEffect(() => {
    return () => {
      cancelPoll.current = true;
    };
  }, []);

  const code = codeByLang[language];
  const setCode = useCallback(
    (next: string) => setCodeByLang((prev) => ({ ...prev, [language]: next })),
    [language],
  );

  const resetCode = useCallback(() => {
    if (!problem) return;
    setCodeByLang((prev) => ({
      ...prev,
      [language]: problem.starterCode[language] ?? '',
    }));
  }, [problem, language]);

  // Poll a submission until terminal. We intentionally DO NOT push the
  // intermediate (PENDING/JUDGING) snapshots into the console — surfacing a
  // half-filled case list mid-run reads as a wrong "0/0" result. The console
  // keeps showing the RUNNING spinner until the verdict is final, then we map
  // the terminal submission once.
  const pollSubmission = useCallback(
    async (submissionId: string): Promise<SubmissionDetail | null> => {
      cancelPoll.current = false;
      for (let i = 0; i < POLL_MAX; i++) {
        await new Promise((r) => setTimeout(r, POLL_MS));
        if (cancelPoll.current) return null;
        const sub = await getSubmission(submissionId);
        if (sub.status === 'DONE' || sub.status === 'FAILED') {
          setRunResult(mapSubmission(sub, problem));
          return sub;
        }
      }
      return null;
    },
    [problem],
  );

  const handleRun = useCallback(async () => {
    if (!problem || busy) return;
    setBusy(true);
    setActionError(null);
    setVerdict(null);
    setRunResult({ status: 'RUNNING', compileError: null, cases: [] });
    try {
      const { submissionId } = await runProblem(problem.id, { language, code: codeByLang[language] });
      const done = await pollSubmission(submissionId);
      if (!done) setActionError('Chạy quá lâu — thử lại hoặc nộp bài.');
    } catch (err) {
      setRunResult(null);
      setActionError(err instanceof ApiError ? err.message : 'Không chạy được code.');
    } finally {
      setBusy(false);
    }
  }, [problem, busy, language, codeByLang, pollSubmission]);

  const handleSubmit = useCallback(async () => {
    if (!problem || busy) return;
    setBusy(true);
    setActionError(null);
    setVerdict(null);
    setRunResult({ status: 'RUNNING', compileError: null, cases: [] });
    try {
      const { submissionId } = await submitProblem(problem.id, { language, code: codeByLang[language] });
      const done = await pollSubmission(submissionId);
      if (done) {
        setVerdict({ verdict: done.verdict, passed: done.passedCases, total: done.totalCases });
        onSubmitted?.(done.verdict);
      } else {
        setActionError('Chấm quá lâu — xem lại ở mục Lịch sử nộp.');
      }
    } catch (err) {
      setRunResult(null);
      setActionError(err instanceof ApiError ? err.message : 'Không nộp được bài.');
    } finally {
      setBusy(false);
    }
  }, [problem, busy, language, codeByLang, pollSubmission, onSubmitted]);

  // ── Divider drag ──────────────────────────────────────────────────────
  const onDragX = (e: React.MouseEvent) => {
    e.preventDefault();
    const move = (ev: MouseEvent) => {
      const pct = (ev.clientX / window.innerWidth) * 100;
      setLeftPct(Math.min(70, Math.max(25, pct)));
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };
  const rightRef = useRef<HTMLDivElement>(null);
  const onDragY = (e: React.MouseEvent) => {
    e.preventDefault();
    const move = (ev: MouseEvent) => {
      const box = rightRef.current?.getBoundingClientRect();
      if (!box) return;
      const pct = ((box.bottom - ev.clientY) / box.height) * 100;
      setConsolePct(Math.min(70, Math.max(15, pct)));
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };

  if (loadError) {
    return (
      <div className={`grid h-screen place-items-center px-6 text-center ${t.surface}`}>
        <div>
          <p className={`mb-4 text-sm ${t.textBody}`}>{loadError}</p>
          <button
            type="button"
            onClick={onExit}
            className={`rounded-xl border px-4 py-2 text-sm font-semibold ${t.btnSecondary}`}
          >
            Về danh sách đề
          </button>
        </div>
      </div>
    );
  }

  if (!problem) {
    return (
      <div className={`grid h-screen place-items-center ${t.surface}`}>
        <span className={`flex items-center gap-2 text-sm ${t.textBody}`}>
          <Loader2 className="h-4 w-4 animate-spin" /> Đang tải đề bài…
        </span>
      </div>
    );
  }

  const diffStyle = difficulty
    ? difficultyStyle(dark)[difficulty as 'EASY' | 'MEDIUM' | 'HARD']?.cls ?? null
    : null;

  return (
    <div className={`flex h-screen flex-col ${t.workspace}`}>
      {/* Top bar */}
      <header className={`flex h-12 shrink-0 items-center justify-between border-b px-3 ${t.border} ${t.surface}`}>
        <div className="flex min-w-0 items-center gap-2">
          <button
            type="button"
            onClick={onExit}
            disabled={busy}
            className={`flex items-center gap-1 rounded-md px-2 py-1 text-sm disabled:opacity-50 ${t.btnGhost}`}
          >
            <ChevronLeft className="h-4 w-4" /> Đề
          </button>
          <span className={`truncate text-sm font-semibold ${t.textStrong}`}>{problem.title}</span>
          {difficulty && diffStyle && (
            <span className={`shrink-0 rounded-md px-2 py-0.5 text-[11px] font-semibold ${diffStyle}`}>
              {DIFFICULTY_LABEL[difficulty] ?? difficulty}
            </span>
          )}
        </div>
      </header>

      {/* Body */}
      <div className="flex min-h-0 flex-1">
        <div style={{ width: `${leftPct}%` }} className={`min-w-0 border-r ${t.border}`}>
          <ProblemStatement problem={problem} dark={dark} />
        </div>

        <div onMouseDown={onDragX} className={`w-1.5 shrink-0 cursor-col-resize transition-colors ${t.divider}`} />

        <div ref={rightRef} style={{ width: `${100 - leftPct}%` }} className="flex min-w-0 flex-1 flex-col">
          {/* Editor toolbar */}
          <div className={`flex h-10 shrink-0 items-center justify-between border-b px-3 ${t.border} ${t.surface}`}>
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value as CodingLanguage)}
              className={`rounded-md border px-2 py-1 text-xs font-medium outline-none focus:border-sky-500 ${t.select}`}
            >
              {CODING_LANGUAGES.map((l) => (
                <option key={l} value={l}>
                  {LANGUAGE_META[l].label}
                </option>
              ))}
            </select>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={toggleAppearance}
                className={`flex items-center gap-1 rounded-md px-2 py-1 text-xs ${t.btnGhost}`}
                title={dark ? 'Chuyển nền sáng' : 'Chuyển nền tối'}
              >
                {dark ? <Sun className="h-3.5 w-3.5" /> : <Moon className="h-3.5 w-3.5" />}
                {dark ? 'Sáng' : 'Tối'}
              </button>
              <button
                type="button"
                onClick={resetCode}
                className={`flex items-center gap-1 rounded-md px-2 py-1 text-xs ${t.btnGhost}`}
                title="Khôi phục code khởi tạo"
              >
                <RotateCcw className="h-3.5 w-3.5" /> Reset
              </button>
            </div>
          </div>

          {/* Editor */}
          <div className="min-h-0 flex-1" style={{ height: `${100 - consolePct}%` }}>
            <CodeEditor
              value={code}
              language={language}
              onChange={setCode}
              onRun={handleRun}
              onSubmit={handleSubmit}
              readOnly={busy}
              appearance={appearance}
            />
          </div>

          <div onMouseDown={onDragY} className={`h-1.5 shrink-0 cursor-row-resize transition-colors ${t.divider}`} />

          {/* Console */}
          <div className="min-h-0 overflow-hidden" style={{ height: `${consolePct}%` }}>
            {verdict ? (
              <VerdictBanner verdict={verdict} dark={dark} />
            ) : (
              <TestcasePanel
                sampleCases={problem.sampleTestCases}
                runResult={runResult}
                running={busy}
                dark={dark}
              />
            )}
          </div>

          {/* Action bar */}
          <div className={`flex h-12 shrink-0 items-center justify-between border-t px-3 ${t.border} ${t.surface}`}>
            <span className={`truncate text-xs ${t.textMuted}`}>
              {actionError ?? '⌘/Ctrl + ↵ để Chạy · ⌘/Ctrl + ⇧ + ↵ để Nộp'}
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleRun}
                disabled={busy}
                className={`flex items-center gap-1.5 rounded-lg border px-3.5 py-1.5 text-sm font-semibold disabled:opacity-50 ${t.btnSecondary}`}
              >
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
                Chạy
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={busy}
                className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3.5 py-1.5 text-sm font-semibold text-white hover:bg-emerald-500 disabled:opacity-50"
              >
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                Nộp bài
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Problem statement (title + description + examples + constraints) ───────
function ProblemStatement({ problem, dark }: { problem: PracticeProblemDetail; dark: boolean }) {
  const t = cwTokens(dark);
  const examples = problem.sampleTestCases ?? [];
  const constraints = problem.constraints?.trim();
  const fmtMap = (m: Record<string, unknown>) =>
    Object.entries(m)
      .map(([k, v]) => `${k} = ${JSON.stringify(v)}`)
      .join(', ');

  return (
    <div className={`h-full overflow-y-auto px-5 py-4 ${t.panel}`}>
      <h1 className={`mb-4 text-lg font-bold ${t.textStrong}`}>{problem.title}</h1>
      <Markdown source={problem.description} dark={dark} />

      {examples.length > 0 && (
        <div className="mt-5 space-y-4">
          {examples.map((tc, idx) => (
            <div key={tc.id ?? idx}>
              <p className={`mb-1.5 text-sm font-semibold ${t.textStrong}`}>Ví dụ {idx + 1}:</p>
              <div className={`rounded-lg border px-3.5 py-2.5 text-[13px] leading-relaxed ${t.border} ${dark ? 'bg-zinc-900/60' : 'bg-zinc-50'}`}>
                <p className={t.textBody}>
                  <span className={`font-semibold ${t.textStrong}`}>Input: </span>
                  <code className="font-mono">{fmtMap(tc.inputData)}</code>
                </p>
                <p className={`mt-1 ${t.textBody}`}>
                  <span className={`font-semibold ${t.textStrong}`}>Output: </span>
                  <code className="font-mono">{fmtMap(tc.expectedOutput)}</code>
                </p>
                {tc.note && tc.note.trim() && (
                  <p className={`mt-1.5 text-[12px] ${t.textMuted}`}>{tc.note}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {constraints && (
        <div className="mt-5">
          <p className={`mb-1.5 text-sm font-semibold ${t.textStrong}`}>Ràng buộc:</p>
          <div className={`text-[13px] ${t.textBody}`}>
            <Markdown source={constraints} dark={dark} />
          </div>
        </div>
      )}

      {(problem.optimalTimeComplexity || problem.optimalSpaceComplexity) && (
        <div className={`mt-5 text-[12px] ${t.textMuted}`}>
          {problem.optimalTimeComplexity && <span>Thời gian tối ưu: {problem.optimalTimeComplexity} </span>}
          {problem.optimalSpaceComplexity && <span>· Bộ nhớ: {problem.optimalSpaceComplexity}</span>}
        </div>
      )}
    </div>
  );
}

// ── SUBMIT verdict banner ──────────────────────────────────────────────────
const VERDICT_LABEL: Record<string, string> = {
  AC: 'Accepted',
  WA: 'Sai kết quả',
  TLE: 'Quá thời gian',
  MLE: 'Quá bộ nhớ',
  RE: 'Lỗi thực thi',
  CE: 'Lỗi biên dịch',
};

function VerdictBanner({
  verdict,
  dark,
}: {
  verdict: { verdict: string | null; passed: number; total: number };
  dark: boolean;
}) {
  const t = cwTokens(dark);
  const accepted = verdict.verdict === 'AC';
  return (
    <div className={`flex h-full flex-col items-center justify-center gap-2 ${t.panel}`}>
      {accepted ? (
        <CheckCircle2 className="h-10 w-10 text-emerald-500" />
      ) : (
        <XCircle className="h-10 w-10 text-rose-500" />
      )}
      <p className={`text-lg font-bold ${accepted ? 'text-emerald-500' : 'text-rose-500'}`}>
        {verdict.verdict ? VERDICT_LABEL[verdict.verdict] ?? verdict.verdict : 'Không xác định'}
      </p>
      <p className={`text-sm ${t.textBody}`}>
        Vượt {verdict.passed}/{verdict.total} test case
      </p>
    </div>
  );
}

// ── Submission → console view mapping ──────────────────────────────────────
function mapSubmission(
  sub: SubmissionDetail,
  problem: PracticeProblemDetail | null,
): RunResultView {
  const expectedById = new Map<string, string>();
  for (const tc of problem?.sampleTestCases ?? []) {
    const values = Object.values(tc.expectedOutput ?? {});
    expectedById.set(tc.id, values.length === 1 ? JSON.stringify(values[0]) : JSON.stringify(tc.expectedOutput ?? null));
  }

  const cases: RunCaseResult[] = (sub.cases ?? []).map((c) => ({
    index: c.orderIndex,
    testCaseId: c.testCaseId ?? '',
    status: mapCaseStatus(c.status),
    stdout: c.stdout,
    expected: c.testCaseId ? expectedById.get(c.testCaseId) ?? null : null,
    stderr: c.stderr,
    runtimeMs: c.runtimeMs,
    memoryKb: c.memoryKb,
  }));

  const ce = (sub.cases ?? []).find((c) => (c.status ?? '').toUpperCase() === 'CE');

  return {
    status: mapStatus(sub.status),
    compileError: ce ? ce.stderr ?? 'Lỗi biên dịch' : null,
    cases,
  };
}

function mapStatus(s: string): RunStatus {
  switch ((s ?? '').toUpperCase()) {
    case 'PENDING':
      return 'PENDING';
    case 'JUDGING':
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
      return 'ERROR';
  }
}
