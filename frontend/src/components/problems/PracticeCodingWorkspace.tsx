import { useCallback, useEffect, useRef, useState } from 'react';
import {
  CheckCircle2,
  ChevronLeft,
  CircleDot,
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
  ProblemStatus,
  RevealedCase,
  SubmissionDetail,
} from '@/types/practice';
import useIsDesktop from '@/lib/useIsDesktop';
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
  EASY: 'Easy',
  MEDIUM: 'Medium',
  HARD: 'Hard',
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
  const [myStatus, setMyStatus] = useState<ProblemStatus>('NONE');
  const [loadError, setLoadError] = useState<string | null>(null);

  const [language, setLanguage] = useState<CodingLanguage>('java');
  const [codeByLang, setCodeByLang] = useState<Record<CodingLanguage, string>>({
    java: '',
    python: '',
    cpp: '',
    javascript: '',
  });

  const [busy, setBusy] = useState(false); // a run/submit is in flight
  const [pendingMode, setPendingMode] = useState<'RUN' | 'SUBMIT' | null>(null);
  const [runResult, setRunResult] = useState<RunResultView | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // Banner for the last SUBMIT verdict (run results show in the console).
  const [verdict, setVerdict] = useState<{
    verdict: string | null;
    passed: number;
    total: number;
    revealedCase: RevealedCase | null;
    compileError: string | null;
  } | null>(null);

  const [leftPct, setLeftPct] = useState(44);
  const [consolePct, setConsolePct] = useState(40);

  // Below md: stacked tab layout instead of the resizable split-pane.
  const isDesktop = useIsDesktop();
  const [mobileTab, setMobileTab] = useState<'problem' | 'code' | 'result'>(
    'problem',
  );

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
        setMyStatus(p.myStatus);
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
    setPendingMode('RUN');
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
    setPendingMode('SUBMIT');
    setActionError(null);
    setVerdict(null);
    setRunResult({ status: 'RUNNING', compileError: null, cases: [] });
    try {
      const { submissionId } = await submitProblem(problem.id, { language, code: codeByLang[language] });
      const done = await pollSubmission(submissionId);
      if (done) {
        const compileError =
          done.verdict === 'CE'
            ? done.cases?.find((c) => (c.status ?? '').toUpperCase() === 'CE')?.stderr ?? 'Lỗi biên dịch'
            : null;
        setVerdict({
          verdict: done.verdict,
          passed: done.passedCases,
          total: done.totalCases,
          revealedCase: done.revealedCase ?? null,
          compileError,
        });
        // Reflect the new standing in the header immediately. SOLVED is sticky —
        // a later non-AC submit never demotes an already-solved problem.
        setMyStatus((prev) =>
          done.verdict === 'AC' ? 'SOLVED' : prev === 'SOLVED' ? 'SOLVED' : 'ATTEMPTED',
        );
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
      <div className={`grid h-[100dvh] place-items-center px-6 text-center ${t.surface}`}>
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
      <div className={`grid h-[100dvh] place-items-center ${t.surface}`}>
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
    <div className={`flex h-[100dvh] flex-col ${t.workspace}`}>
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
          <StatusBadge status={myStatus} />
        </div>
      </header>

      {/* Mobile tab switcher — one panel at a time below md (no split-pane). */}
      <div className={`md:hidden flex shrink-0 border-b ${t.border} ${t.surface}`}>
        {(
          [
            ['problem', 'Đề bài'],
            ['code', 'Code'],
            ['result', 'Kết quả'],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setMobileTab(key)}
            className={`flex-1 py-2.5 text-sm font-medium transition-colors ${
              mobileTab === key
                ? dark
                  ? 'border-b-2 border-sky-400 text-sky-400'
                  : 'border-b-2 border-sky-600 text-sky-600'
                : t.textMuted
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Body (stacks into tabs below md) */}
      <div className="flex min-h-0 flex-1 flex-col md:flex-row">
        <div
          style={isDesktop ? { width: `${leftPct}%` } : undefined}
          className={`min-w-0 flex-1 md:flex-initial md:border-r ${t.border} ${
            mobileTab === 'problem' ? 'block' : 'hidden'
          } md:block`}
        >
          <ProblemStatement problem={problem} difficulty={difficulty} dark={dark} />
        </div>

        <div onMouseDown={onDragX} className={`hidden md:block w-1.5 shrink-0 cursor-col-resize transition-colors ${t.divider}`} />

        <div
          ref={rightRef}
          style={isDesktop ? { width: `${100 - leftPct}%` } : undefined}
          className={`min-w-0 flex-1 flex-col md:flex ${
            mobileTab === 'problem' ? 'hidden' : 'flex'
          }`}
        >
          {/* Editor toolbar */}
          <div
            className={`h-10 shrink-0 items-center justify-between border-b px-3 ${t.border} ${t.surface} ${
              mobileTab === 'result' ? 'hidden md:flex' : 'flex'
            }`}
          >
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
          <div
            className={`min-h-0 md:block md:flex-1 ${
              mobileTab === 'code' ? 'flex-1' : 'hidden'
            }`}
            style={isDesktop ? { height: `${100 - consolePct}%` } : undefined}
          >
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

          <div onMouseDown={onDragY} className={`hidden md:block h-1.5 shrink-0 cursor-row-resize transition-colors ${t.divider}`} />

          {/* Console */}
          <div
            className={`min-h-0 overflow-hidden md:block ${
              mobileTab === 'result' ? 'flex-1' : 'hidden'
            }`}
            style={isDesktop ? { height: `${consolePct}%` } : undefined}
          >
            {verdict ? (
              <VerdictBanner verdict={verdict} dark={dark} />
            ) : (
              <TestcasePanel
                sampleCases={problem.sampleTestCases}
                runResult={runResult}
                running={busy}
                mode={pendingMode}
                dark={dark}
              />
            )}
          </div>

          {/* Action bar */}
          <div className={`flex h-12 shrink-0 items-center justify-between border-t px-3 ${t.border} ${t.surface}`}>
            <span
              className={`truncate text-xs ${t.textMuted} ${
                actionError ? 'inline' : 'hidden sm:inline'
              }`}
            >
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

// ── Solve-status badge (header) ─────────────────────────────────────────────

/** LeetCode-style indicator: green tick once solved, amber dot while only
 *  attempted, nothing until the user has submitted at least once. */
function StatusBadge({ status }: { status: ProblemStatus }) {
  if (status === 'SOLVED') {
    return (
      <span
        className="flex shrink-0 items-center gap-1 rounded-md bg-emerald-500/10 px-2 py-0.5 text-[11px] font-semibold text-emerald-500"
        title="Bạn đã giải bài này"
      >
        <CheckCircle2 className="h-3.5 w-3.5" /> Đã giải
      </span>
    );
  }
  if (status === 'ATTEMPTED') {
    return (
      <span
        className="flex shrink-0 items-center gap-1 rounded-md bg-amber-500/10 px-2 py-0.5 text-[11px] font-semibold text-amber-500"
        title="Bạn đã nộp nhưng chưa giải được"
      >
        <CircleDot className="h-3.5 w-3.5" /> Đã thử
      </span>
    );
  }
  return null;
}

// ── Problem statement (LeetCode-style: title · difficulty · description ·
//    examples · constraints · complexity) ────────────────────────────────────
function fmtVal(value: unknown): string {
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function ProblemStatement({
  problem,
  difficulty,
  dark,
}: {
  problem: PracticeProblemDetail;
  difficulty?: string | null;
  dark: boolean;
}) {
  const t = cwTokens(dark);
  const examples = problem.sampleTestCases ?? [];
  const constraints = problem.constraints?.trim();
  const diff = difficulty
    ? difficultyStyle(dark)[difficulty as 'EASY' | 'MEDIUM' | 'HARD'] ?? null
    : null;

  // Inputs render as `name = value` (LeetCode); the output shows only the
  // value(s), never the internal `result` wrapper key.
  const fmtInputs = (m: Record<string, unknown>) =>
    Object.entries(m)
      .map(([k, v]) => `${k} = ${fmtVal(v)}`)
      .join(', ');
  const fmtOutputs = (m: Record<string, unknown>) =>
    Object.values(m).map(fmtVal).join(', ');

  return (
    <div className={`h-full overflow-y-auto px-6 py-5 ${t.panel}`}>
      {/* Title + difficulty pill */}
      <h1 className={`text-xl font-bold leading-snug ${t.textStrong}`}>
        {problem.title}
      </h1>
      {diff && (
        <div className="mt-2.5">
          <span
            className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${diff.cls}`}
          >
            {diff.label}
          </span>
        </div>
      )}

      <div className="mt-4">
        <Markdown source={problem.description} dark={dark} />
      </div>

      {examples.length > 0 && (
        <div className="mt-6 space-y-5">
          {examples.map((tc, idx) => (
            <div key={tc.id ?? idx}>
              <p className={`mb-2 text-[15px] font-semibold ${t.textStrong}`}>
                Ví dụ {idx + 1}:
              </p>
              {/* LeetCode example block: left accent bar + soft fill. */}
              <div
                className={`rounded-md border-l-4 px-4 py-3 text-[13px] leading-7 ${
                  dark
                    ? 'border-l-zinc-600 bg-zinc-900/60'
                    : 'border-l-zinc-300 bg-zinc-100/70'
                }`}
              >
                <p className={t.textBody}>
                  <span className={`font-semibold ${t.textStrong}`}>Input: </span>
                  <code className="font-mono">{fmtInputs(tc.inputData)}</code>
                </p>
                <p className={t.textBody}>
                  <span className={`font-semibold ${t.textStrong}`}>Output: </span>
                  <code className="font-mono">{fmtOutputs(tc.expectedOutput)}</code>
                </p>
                {tc.note && tc.note.trim() && (
                  <p className={`mt-0.5 ${t.textBody}`}>
                    <span className={`font-semibold ${t.textStrong}`}>
                      Giải thích:{' '}
                    </span>
                    {tc.note}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {constraints && (
        <div className="mt-6">
          <p className={`mb-2 text-[15px] font-semibold ${t.textStrong}`}>
            Ràng buộc:
          </p>
          <div className={`text-[13px] ${t.textBody}`}>
            <Markdown source={constraints} dark={dark} />
          </div>
        </div>
      )}

      {(problem.optimalTimeComplexity || problem.optimalSpaceComplexity) && (
        <div className={`mt-6 flex flex-wrap gap-2 border-t pt-4 ${t.border}`}>
          {problem.optimalTimeComplexity && (
            <span
              className={`rounded-md px-2.5 py-1 text-xs font-medium ${t.chip}`}
            >
              Thời gian tối ưu: {problem.optimalTimeComplexity}
            </span>
          )}
          {problem.optimalSpaceComplexity && (
            <span
              className={`rounded-md px-2.5 py-1 text-xs font-medium ${t.chip}`}
            >
              Bộ nhớ tối ưu: {problem.optimalSpaceComplexity}
            </span>
          )}
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
  verdict: {
    verdict: string | null;
    passed: number;
    total: number;
    revealedCase: RevealedCase | null;
    compileError: string | null;
  };
  dark: boolean;
}) {
  const t = cwTokens(dark);
  const accepted = verdict.verdict === 'AC';
  const label = verdict.verdict
    ? VERDICT_LABEL[verdict.verdict] ?? verdict.verdict
    : 'Không xác định';

  // Accepted: celebrate and show the full pass count — the only time a count is shown.
  if (accepted) {
    return (
      <div className={`flex h-full flex-col items-center justify-center gap-2 ${t.panel}`}>
        <CheckCircle2 className="h-10 w-10 text-emerald-500" />
        <p className="text-lg font-bold text-emerald-500">{label}</p>
        <p className={`text-sm ${t.textBody}`}>
          Vượt {verdict.passed}/{verdict.total} test case
        </p>
      </div>
    );
  }

  // Failed: no pass count. Show the compile error, or a single revealed test case.
  return (
    <div className={`flex h-full flex-col gap-3 overflow-auto p-4 ${t.panel}`}>
      <div className="flex items-center gap-2">
        <XCircle className="h-6 w-6 shrink-0 text-rose-500" />
        <p className="text-base font-bold text-rose-500">{label}</p>
      </div>

      {verdict.compileError ? (
        <pre className={`whitespace-pre-wrap rounded-md p-3 text-xs ${t.codeBlock}`}>
          {verdict.compileError}
        </pre>
      ) : verdict.revealedCase ? (
        <div className={`flex flex-col gap-3 rounded-md border p-3 ${t.card}`}>
          <p className={`text-xs font-semibold ${t.textMuted}`}>
            Test case bị fail (ẩn) — sửa lại lời giải của bạn:
          </p>
          <RevealedField label="Input" value={verdict.revealedCase.input} t={t} />
          <RevealedField label="Đáp án đúng" value={verdict.revealedCase.expected} t={t} />
          <RevealedField
            label="Output của bạn"
            value={verdict.revealedCase.actualOutput ?? '(không có)'}
            t={t}
          />
        </div>
      ) : (
        <p className={`text-sm ${t.textBody}`}>
          Bài làm chưa đúng. Hãy xem lại lời giải và thử lại.
        </p>
      )}
    </div>
  );
}

function RevealedField({
  label,
  value,
  t,
}: {
  label: string;
  value: string;
  t: ReturnType<typeof cwTokens>;
}) {
  return (
    <div className="flex flex-col gap-1">
      <span className={`text-[11px] font-medium uppercase tracking-wide ${t.textMuted}`}>
        {label}
      </span>
      <pre className={`whitespace-pre-wrap break-all rounded p-2 text-xs ${t.codeBlock}`}>
        {value}
      </pre>
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
