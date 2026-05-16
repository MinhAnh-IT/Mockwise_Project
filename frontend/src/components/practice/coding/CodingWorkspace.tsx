import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  ChevronLeft,
  Clock,
  Loader2,
  Moon,
  Play,
  RotateCcw,
  Send,
  Sun,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getCodingProblem, getRunResult, runCode } from '@/api/coding';
import {
  CODING_LANGUAGES,
  LANGUAGE_META,
  type CodingLanguage,
  type CodingProblemView,
  type RunResultView,
} from '@/types/coding';
import type { PinnedQuestionView } from '@/types/interview';
import CodeEditor from './CodeEditor';
import ProblemPanel from './ProblemPanel';
import TestcasePanel from './TestcasePanel';
import { buildMockProblem } from './mockProblem';
import {
  CW_THEME_STORAGE_KEY,
  cwTokens,
  readStoredAppearance,
  type CwAppearance,
} from './theme';

type Props = {
  sessionId: string;
  question: PinnedQuestionView;
  budget: number;
  /** Parent is running the final submit / finishing the session. */
  busy: boolean;
  /** Delegates to the existing CODE answer flow on PracticeSessionPage. */
  onSubmit: (code: string, language: CodingLanguage) => void;
  onExit: () => void;
  /**
   * Pure-mock mode for /practice/coding/preview: never touches the API
   * (no backend / no auth needed) and Run is simulated locally.
   */
  previewMode?: boolean;
};

const RUN_POLL_MS = 1200;
const RUN_POLL_MAX = 50; // ~60s ceiling

/**
 * Full-screen LeetCode-style coding workspace. Owns: problem fetch (with a
 * demo fallback until the BE contract lands), per-language code buffers, the
 * countdown timer (auto-submits at 0), Run (sample tests via the judge
 * passthrough), and Submit (delegated to the parent's CODE answer flow).
 */
export default function CodingWorkspace({
  sessionId,
  question,
  budget,
  busy,
  onSubmit,
  onExit,
  previewMode = false,
}: Props) {
  const sqId = question.sessionQuestionId;

  const [problem, setProblem] = useState<CodingProblemView | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [usingMock, setUsingMock] = useState(false);

  const [language, setLanguage] = useState<CodingLanguage>('java');
  const [codeByLang, setCodeByLang] = useState<Record<CodingLanguage, string>>(
    { java: '', python: '', cpp: '', javascript: '' },
  );

  const [running, setRunning] = useState(false);
  const [runResult, setRunResult] = useState<RunResultView | null>(null);
  const [runError, setRunError] = useState<string | null>(null);

  const [secondsLeft, setSecondsLeft] = useState<number | null>(null);

  // Split-pane sizing (problem width %, console height %).
  const [leftPct, setLeftPct] = useState(42);
  const [consolePct, setConsolePct] = useState(38);

  // Appearance — defaults to LIGHT; user flips to dark with the toolbar
  // toggle and the choice is remembered.
  const [appearance, setAppearance] = useState<CwAppearance>(
    readStoredAppearance,
  );
  const dark = appearance === 'dark';
  const t = cwTokens(dark);
  const toggleAppearance = useCallback(() => {
    setAppearance((prev) => {
      const next = prev === 'dark' ? 'light' : 'dark';
      try {
        window.localStorage.setItem(CW_THEME_STORAGE_KEY, next);
      } catch {
        /* private mode / storage disabled — non-fatal */
      }
      return next;
    });
  }, []);

  // ── Load the problem (fall back to demo data if the BE isn't ready) ──────
  useEffect(() => {
    let cancelled = false;
    setProblem(null);
    setLoadError(null);
    setRunResult(null);
    setRunError(null);

    // Preview: skip the network entirely so the page works with no
    // backend and no auth token.
    if (previewMode) {
      applyProblem(buildMockProblem(sqId, question.sequence), true);
      return () => {
        cancelled = true;
      };
    }

    (async () => {
      try {
        const p = await getCodingProblem(sessionId, sqId);
        if (cancelled) return;
        applyProblem(p, false);
      } catch (err) {
        if (cancelled) return;
        // 404 / 501 ⇒ endpoint not implemented yet → demo fallback so the
        // UI stays reviewable. Other errors surface to the user.
        if (
          err instanceof ApiError &&
          (err.status === 404 || err.status === 501)
        ) {
          applyProblem(buildMockProblem(sqId, question.sequence), true);
        } else {
          setLoadError(
            err instanceof ApiError
              ? err.message
              : 'Không tải được đề bài lập trình.',
          );
        }
      }
    })();

    function applyProblem(p: CodingProblemView, mock: boolean) {
      setProblem(p);
      setUsingMock(mock);
      setCodeByLang({
        java: p.starterCode.java ?? '',
        python: p.starterCode.python ?? '',
        cpp: p.starterCode.cpp ?? '',
        javascript: p.starterCode.javascript ?? '',
      });
      setSecondsLeft(p.timeLimitMinutes * 60);
    }

    return () => {
      cancelled = true;
    };
  }, [sessionId, sqId, question.sequence, previewMode]);

  // ── Countdown — auto-submits once when it hits zero ─────────────────────
  const autoSubmittedRef = useRef(false);
  useEffect(() => {
    autoSubmittedRef.current = false;
  }, [sqId]);

  useEffect(() => {
    if (secondsLeft == null) return;
    if (secondsLeft <= 0) return;
    const t = window.setInterval(() => {
      setSecondsLeft((s) => (s == null ? s : Math.max(0, s - 1)));
    }, 1000);
    return () => window.clearInterval(t);
  }, [secondsLeft != null]);

  useEffect(() => {
    if (
      secondsLeft === 0 &&
      !autoSubmittedRef.current &&
      problem &&
      !busy
    ) {
      autoSubmittedRef.current = true;
      onSubmit(codeByLang[language], language);
    }
  }, [secondsLeft, problem, busy, onSubmit, codeByLang, language]);

  const code = codeByLang[language];
  const setCode = useCallback(
    (next: string) =>
      setCodeByLang((prev) => ({ ...prev, [language]: next })),
    [language],
  );

  const resetCode = useCallback(() => {
    if (!problem) return;
    setCodeByLang((prev) => ({
      ...prev,
      [language]: problem.starterCode[language] ?? '',
    }));
  }, [problem, language]);

  // ── Run against the visible sample cases ────────────────────────────────
  const handleRun = useCallback(async () => {
    if (!problem || running || busy) return;
    setRunError(null);
    setRunResult(null);
    setRunning(true);

    // Preview: simulate a judge round-trip locally (no backend).
    if (previewMode) {
      await new Promise((r) => setTimeout(r, 900));
      setRunResult({
        status: 'DONE',
        compileError: null,
        cases: problem.sampleTestCases.map((tc, i) => {
          const expected = JSON.stringify(
            Object.values(tc.expectedOutput)[0] ?? null,
          );
          return {
            index: i,
            testCaseId: tc.id,
            status: 'PASSED' as const,
            stdout: expected,
            expected,
            stderr: null,
            runtimeMs: 2 + i,
            memoryKb: 14000 + i * 128,
          };
        }),
      });
      setRunning(false);
      return;
    }

    try {
      const { runId } = await runCode(problem, {
        language,
        code: codeByLang[language],
      });
      for (let i = 0; i < RUN_POLL_MAX; i++) {
        await new Promise((r) => setTimeout(r, RUN_POLL_MS));
        const res = await getRunResult(runId, problem);
        if (res.status === 'DONE' || res.status === 'FAILED') {
          setRunResult(res);
          setRunning(false);
          return;
        }
      }
      setRunError('Chạy quá lâu — thử lại hoặc nộp bài để chấm trên server.');
      setRunning(false);
    } catch (err) {
      setRunning(false);
      if (
        err instanceof ApiError &&
        (err.status === 404 || err.status === 501)
      ) {
        setRunError(
          'Chức năng “Chạy thử” cần judge-service (POST /api/v1/judge/submit). Bạn vẫn có thể Nộp bài.',
        );
      } else {
        setRunError(
          err instanceof ApiError ? err.message : 'Không chạy được code.',
        );
      }
    }
  }, [problem, running, busy, sessionId, sqId, language, codeByLang, previewMode]);

  const handleSubmit = useCallback(() => {
    if (!problem || busy) return;
    onSubmit(codeByLang[language], language);
  }, [problem, busy, onSubmit, codeByLang, language]);

  // ── Divider drag handlers ───────────────────────────────────────────────
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

  // ── Render ──────────────────────────────────────────────────────────────
  if (loadError) {
    return (
      <div
        className={`grid h-screen place-items-center px-6 text-center ${t.surface}`}
      >
        <div>
          <p className={`mb-4 text-sm ${t.textBody}`}>{loadError}</p>
          <button
            type="button"
            onClick={onExit}
            className={`rounded-xl border px-4 py-2 text-sm font-semibold ${t.btnSecondary}`}
          >
            Thoát phiên
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

  const timeStr =
    secondsLeft == null
      ? '--:--'
      : `${String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:${String(
          secondsLeft % 60,
        ).padStart(2, '0')}`;
  const lowTime = secondsLeft != null && secondsLeft <= 60;

  return (
    <div className={`flex h-screen flex-col ${t.workspace}`}>
      {/* Top bar */}
      <header
        className={`flex h-12 shrink-0 items-center justify-between border-b px-3 ${t.border} ${t.surface}`}
      >
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onExit}
            disabled={busy}
            className={`flex items-center gap-1 rounded-md px-2 py-1 text-sm disabled:opacity-50 ${t.btnGhost}`}
          >
            <ChevronLeft className="h-4 w-4" /> Thoát
          </button>
          <span className={`text-sm ${t.textMuted}`}>
            Câu {question.sequence} / {budget}
          </span>
          {usingMock && (
            <span
              className={`rounded-md px-2 py-0.5 text-[11px] font-medium ${
                dark
                  ? 'bg-amber-500/15 text-amber-400'
                  : 'bg-amber-100 text-amber-700'
              }`}
            >
              Dữ liệu mẫu (backend coding chưa sẵn sàng)
            </span>
          )}
        </div>
        <div
          className={`flex items-center gap-1.5 rounded-md px-2.5 py-1 text-sm font-semibold tabular-nums ${
            lowTime
              ? dark
                ? 'bg-rose-500/15 text-rose-400'
                : 'bg-rose-100 text-rose-700'
              : t.timerIdle
          }`}
        >
          <Clock className="h-4 w-4" />
          {timeStr}
        </div>
      </header>

      {/* Body: problem | editor+console */}
      <div className="flex min-h-0 flex-1">
        <div
          style={{ width: `${leftPct}%` }}
          className={`min-w-0 border-r ${t.border}`}
        >
          <ProblemPanel problem={problem} dark={dark} />
        </div>

        <div
          onMouseDown={onDragX}
          className={`w-1.5 shrink-0 cursor-col-resize transition-colors ${t.divider}`}
        />

        <div
          ref={rightRef}
          style={{ width: `${100 - leftPct}%` }}
          className="flex min-w-0 flex-1 flex-col"
        >
          {/* Editor toolbar */}
          <div
            className={`flex h-10 shrink-0 items-center justify-between border-b px-3 ${t.border} ${t.surface}`}
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
                {dark ? (
                  <Sun className="h-3.5 w-3.5" />
                ) : (
                  <Moon className="h-3.5 w-3.5" />
                )}
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
            className="min-h-0 flex-1"
            style={{ height: `${100 - consolePct}%` }}
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

          {/* Horizontal divider */}
          <div
            onMouseDown={onDragY}
            className={`h-1.5 shrink-0 cursor-row-resize transition-colors ${t.divider}`}
          />

          {/* Console */}
          <div
            className="min-h-0 overflow-hidden"
            style={{ height: `${consolePct}%` }}
          >
            <TestcasePanel
              sampleCases={problem.sampleTestCases}
              runResult={runResult}
              running={running}
              dark={dark}
            />
          </div>

          {/* Action bar */}
          <div
            className={`flex h-12 shrink-0 items-center justify-between border-t px-3 ${t.border} ${t.surface}`}
          >
            <span className={`truncate text-xs ${t.textMuted}`}>
              {runError ?? '⌘/Ctrl + ↵ để Chạy · ⌘/Ctrl + ⇧ + ↵ để Nộp'}
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleRun}
                disabled={running || busy}
                className={`flex items-center gap-1.5 rounded-lg border px-3.5 py-1.5 text-sm font-semibold disabled:opacity-50 ${t.btnSecondary}`}
              >
                {running ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Play className="h-4 w-4" />
                )}
                Chạy
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={busy}
                className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3.5 py-1.5 text-sm font-semibold text-white hover:bg-emerald-500 disabled:opacity-50"
              >
                {busy ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Send className="h-4 w-4" />
                )}
                Nộp bài
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
