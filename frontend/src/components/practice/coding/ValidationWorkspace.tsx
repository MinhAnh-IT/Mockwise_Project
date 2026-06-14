import { useCallback, useRef, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  ChevronLeft,
  FlaskConical,
  Loader2,
  Moon,
  Play,
  RotateCcw,
  Sun,
  XCircle,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getRunResult, runCode } from '@/api/coding';
import {
  CODING_LANGUAGES,
  LANGUAGE_META,
  type CodingLanguage,
  type CodingProblemView,
  type RunCaseResult,
  type RunResultView,
  type SampleTestCase,
} from '@/types/coding';
import CodeEditor from './CodeEditor';
import ProblemPanel from './ProblemPanel';
import {
  CW_THEME_STORAGE_KEY,
  cwTokens,
  readStoredAppearance,
  type CwAppearance,
  type CwTokens,
} from './theme';

/**
 * One test case the admin validates against — carries the full suite incl.
 * hidden cases (which the candidate workspace never sees). `id` is synthetic
 * so judge results can be matched back even for freshly-authored cases.
 */
export type ValidationCase = {
  id: string;
  inputData: Record<string, unknown>;
  expectedOutput: Record<string, unknown>;
  is_hidden: boolean;
  note?: string | null;
};

type Props = {
  /** Drives the left problem pane (visible samples only, as a candidate sees). */
  problem: CodingProblemView;
  /** Full suite the Run is graded against — hidden cases included. */
  allCases: ValidationCase[];
  onExit: () => void;
};

const RUN_POLL_MS = 1200;
const RUN_POLL_MAX = 50; // ~60s ceiling

/**
 * Admin-only "Kiểm tra đề" workspace — a full-screen overlay that reuses the
 * candidate coding UI to verify a question BEFORE it is saved. The admin pastes
 * a reference solution and Runs it against the WHOLE suite (hidden cases too);
 * nothing is persisted (it hits the throwaway judge passthrough). There is no
 * Submit — only Run — and every test case is listed below the editor.
 */
export default function ValidationWorkspace({
  problem,
  allCases,
  onExit,
}: Props) {
  const [language, setLanguage] = useState<CodingLanguage>('java');
  const [codeByLang, setCodeByLang] = useState<Record<CodingLanguage, string>>({
    java: problem.starterCode.java ?? '',
    python: problem.starterCode.python ?? '',
    cpp: problem.starterCode.cpp ?? '',
    javascript: problem.starterCode.javascript ?? '',
  });

  const [running, setRunning] = useState(false);
  const [runResult, setRunResult] = useState<RunResultView | null>(null);
  const [runError, setRunError] = useState<string | null>(null);

  const [leftPct, setLeftPct] = useState(42);
  const [consolePct, setConsolePct] = useState(42);

  const [appearance, setAppearance] = useState<CwAppearance>(readStoredAppearance);
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

  const code = codeByLang[language];
  const setCode = useCallback(
    (next: string) => setCodeByLang((prev) => ({ ...prev, [language]: next })),
    [language],
  );
  const resetCode = useCallback(() => {
    setCodeByLang((prev) => ({
      ...prev,
      [language]: problem.starterCode[language] ?? '',
    }));
  }, [problem, language]);

  // The judge submission only needs id/inputData/expectedOutput — reuse the
  // sample-case shape so runCode/getRunResult can be shared with the candidate
  // flow (here we feed it ALL cases, hidden included).
  const judgeCases: SampleTestCase[] = allCases.map((c) => ({
    id: c.id,
    inputData: c.inputData,
    expectedOutput: c.expectedOutput,
  }));

  const handleRun = useCallback(async () => {
    if (running) return;
    setRunError(null);
    setRunResult(null);
    setRunning(true);
    try {
      const { runId } = await runCode(
        problem,
        { language, code: codeByLang[language] },
        judgeCases,
        true, // ephemeral — judge purges these throwaway validation rows
      );
      for (let i = 0; i < RUN_POLL_MAX; i++) {
        await new Promise((r) => setTimeout(r, RUN_POLL_MS));
        const res = await getRunResult(runId, problem, judgeCases);
        if (res.status === 'DONE' || res.status === 'FAILED') {
          setRunResult(res);
          setRunning(false);
          return;
        }
      }
      setRunError('Chạy quá lâu — thử lại.');
      setRunning(false);
    } catch (err) {
      setRunning(false);
      if (err instanceof ApiError && (err.status === 404 || err.status === 501)) {
        setRunError(
          'Chức năng “Chạy thử” cần judge-service (POST /api/v1/judge/submit).',
        );
      } else {
        setRunError(
          err instanceof ApiError ? err.message : 'Không chạy được code.',
        );
      }
    }
  }, [running, problem, language, codeByLang, judgeCases]);

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
      setConsolePct(Math.min(75, Math.max(15, pct)));
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };

  return (
    <div className={`fixed inset-0 z-[70] flex flex-col ${t.workspace}`}>
      {/* Top bar */}
      <header
        className={`flex h-12 shrink-0 items-center justify-between border-b px-3 ${t.border} ${t.surface}`}
      >
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onExit}
            className={`flex items-center gap-1 rounded-md px-2 py-1 text-sm ${t.btnGhost}`}
          >
            <ChevronLeft className="h-4 w-4" /> Thoát
          </button>
          <span
            className={`flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[11px] font-semibold ${
              dark
                ? 'bg-sky-500/15 text-sky-400'
                : 'bg-sky-100 text-sky-700'
            }`}
          >
            <FlaskConical className="h-3.5 w-3.5" />
            Chế độ kiểm tra đề — chưa lưu
          </span>
        </div>
        <button
          type="button"
          onClick={toggleAppearance}
          className={`flex items-center gap-1 rounded-md px-2 py-1 text-xs ${t.btnGhost}`}
          title={dark ? 'Chuyển nền sáng' : 'Chuyển nền tối'}
        >
          {dark ? <Sun className="h-3.5 w-3.5" /> : <Moon className="h-3.5 w-3.5" />}
          {dark ? 'Sáng' : 'Tối'}
        </button>
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
            <button
              type="button"
              onClick={resetCode}
              className={`flex items-center gap-1 rounded-md px-2 py-1 text-xs ${t.btnGhost}`}
              title="Khôi phục code khởi tạo"
            >
              <RotateCcw className="h-3.5 w-3.5" /> Reset
            </button>
          </div>

          {/* Editor */}
          <div className="min-h-0 flex-1" style={{ height: `${100 - consolePct}%` }}>
            <CodeEditor
              value={code}
              language={language}
              onChange={setCode}
              onRun={handleRun}
              onSubmit={handleRun}
              appearance={appearance}
            />
          </div>

          {/* Horizontal divider */}
          <div
            onMouseDown={onDragY}
            className={`h-1.5 shrink-0 cursor-row-resize transition-colors ${t.divider}`}
          />

          {/* Console — all test cases */}
          <div
            className="min-h-0 overflow-hidden"
            style={{ height: `${consolePct}%` }}
          >
            <ValidationConsole
              cases={allCases}
              runResult={runResult}
              running={running}
              t={t}
            />
          </div>

          {/* Action bar — Run only (no Submit) */}
          <div
            className={`flex h-12 shrink-0 items-center justify-between border-t px-3 ${t.border} ${t.surface}`}
          >
            <span className={`truncate text-xs ${t.textMuted}`}>
              {runError ?? `Chạy đối chiếu ${allCases.length} test case · ⌘/Ctrl + ↵`}
            </span>
            <button
              type="button"
              onClick={handleRun}
              disabled={running}
              className={`flex items-center gap-1.5 rounded-lg border px-3.5 py-1.5 text-sm font-semibold disabled:opacity-50 ${t.btnSecondary}`}
            >
              {running ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Play className="h-4 w-4" />
              )}
              Chạy
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Console: lists EVERY test case, with per-case run verdict ───────────────

function fmt(value: unknown): string {
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/** Single-line, horizontally-scrolling value box (no wrapping for long values). */
function ScrollValue({
  value,
  t,
  tone,
}: {
  value: string;
  t: CwTokens;
  tone?: 'ok' | 'bad';
}) {
  const valueCls = tone === 'ok' ? t.ok : tone === 'bad' ? t.bad : t.kvValue;
  return (
    <div
      className={`overflow-x-auto whitespace-nowrap rounded-md border px-2.5 py-1.5 font-mono text-[12.5px] ${t.sigBox} ${valueCls}`}
    >
      {value}
    </div>
  );
}

function ValidationConsole({
  cases,
  runResult,
  running,
  t,
}: {
  cases: ValidationCase[];
  runResult: RunResultView | null;
  running: boolean;
  t: CwTokens;
}) {
  const byId = new Map<string, RunCaseResult>();
  for (const c of runResult?.cases ?? []) {
    if (c.testCaseId) byId.set(c.testCaseId, c);
  }

  const passed = (runResult?.cases ?? []).filter(
    (c) => c.status === 'PASSED',
  ).length;
  const total = runResult?.cases?.length ?? 0;
  const allPass = total > 0 && passed === total;
  const hasResult = runResult != null && !runResult.compileError;

  return (
    <div className={`flex h-full flex-col ${t.panel}`}>
      {/* Summary header */}
      <div
        className={`flex shrink-0 items-center justify-between gap-2 border-b px-4 py-2 ${t.border}`}
      >
        <span className={`text-sm font-semibold ${t.textStrong}`}>
          Test case{' '}
          <span className={`font-normal ${t.textMuted}`}>({cases.length})</span>
        </span>
        {running ? (
          <span className={`flex items-center gap-1.5 text-xs ${t.textBody}`}>
            <Loader2 className="h-3.5 w-3.5 animate-spin text-sky-500" />
            Đang chạy…
          </span>
        ) : hasResult ? (
          <span
            className={`text-sm font-bold ${allPass ? t.ok : t.bad}`}
          >
            {allPass ? 'Tất cả PASS' : 'Có case sai'} · {passed}/{total}
          </span>
        ) : null}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        {runResult?.compileError ? (
          <div>
            <div
              className={`mb-2 flex items-center gap-2 text-sm font-semibold ${t.bad}`}
            >
              <AlertTriangle className="h-4 w-4" />
              Lỗi biên dịch
            </div>
            <pre
              className={`overflow-x-auto rounded-lg px-3 py-2.5 text-[13px] leading-relaxed ${
                t.dark ? 'bg-zinc-900 text-rose-300' : 'bg-rose-50 text-rose-700'
              }`}
            >
              {runResult.compileError}
            </pre>
          </div>
        ) : (
          <div className="space-y-3">
            {cases.map((c, i) => {
              const r = byId.get(c.id);
              const ok = r?.status === 'PASSED';
              return (
                <div
                  key={c.id}
                  className={`rounded-lg border p-3 ${t.card}`}
                >
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <span className="flex items-center gap-1.5 text-sm font-semibold">
                      {r ? (
                        ok ? (
                          <CheckCircle2 className={`h-4 w-4 ${t.ok}`} />
                        ) : (
                          <XCircle className={`h-4 w-4 ${t.bad}`} />
                        )
                      ) : null}
                      <span
                        className={r ? (ok ? t.ok : t.bad) : t.textStrong}
                      >
                        Case {i + 1}
                        {r ? ` · ${r.status}` : ''}
                      </span>
                      {c.is_hidden && (
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${
                            t.dark
                              ? 'bg-zinc-800 text-zinc-400'
                              : 'bg-zinc-200 text-zinc-600'
                          }`}
                        >
                          Ẩn
                        </span>
                      )}
                    </span>
                    {r && (
                      <span className={`shrink-0 text-xs ${t.textMuted}`}>
                        {r.runtimeMs != null ? `${r.runtimeMs} ms` : '—'}
                        {r.memoryKb != null
                          ? ` · ${(r.memoryKb / 1024).toFixed(1)} MB`
                          : ''}
                      </span>
                    )}
                  </div>

                  <div className="space-y-2">
                    <div>
                      <div className={`mb-1 text-xs ${t.textMuted}`}>Input</div>
                      <div className="space-y-1.5">
                        {Object.entries(c.inputData).map(([k, v]) => (
                          <ScrollValue key={k} value={`${k} = ${fmt(v)}`} t={t} />
                        ))}
                      </div>
                    </div>
                    {r && (
                      <div>
                        <div className={`mb-1 text-xs ${t.textMuted}`}>
                          Output thực tế
                        </div>
                        <ScrollValue
                          value={r.stdout ?? '—'}
                          t={t}
                          tone={ok ? 'ok' : 'bad'}
                        />
                      </div>
                    )}
                    <div>
                      <div className={`mb-1 text-xs ${t.textMuted}`}>
                        Kết quả mong đợi
                      </div>
                      <ScrollValue
                        value={fmt(
                          Object.values(c.expectedOutput).length === 1
                            ? Object.values(c.expectedOutput)[0]
                            : c.expectedOutput,
                        )}
                        t={t}
                      />
                    </div>
                  </div>

                  {r?.stderr && (
                    <pre
                      className={`mt-2 overflow-x-auto rounded px-2.5 py-1.5 text-[12px] ${
                        t.dark
                          ? 'bg-rose-950/40 text-rose-300'
                          : 'bg-rose-50 text-rose-700'
                      }`}
                    >
                      {r.stderr}
                    </pre>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
