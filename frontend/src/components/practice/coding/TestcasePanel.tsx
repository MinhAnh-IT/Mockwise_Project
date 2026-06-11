import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Loader2, XCircle } from 'lucide-react';
import type { RunResultView, SampleTestCase } from '@/types/coding';
import { cwTokens, type CwTokens } from './theme';

type Tab = 'cases' | 'result';

type Props = {
  sampleCases: SampleTestCase[];
  runResult: RunResultView | null;
  running: boolean;
  dark: boolean;
};

function fmt(value: unknown): string {
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/** Bottom-right console — LeetCode's "Testcase / Result" split. */
export default function TestcasePanel({
  sampleCases,
  runResult,
  running,
  dark,
}: Props) {
  const t = cwTokens(dark);
  const [tab, setTab] = useState<Tab>('cases');
  const [activeCase, setActiveCase] = useState(0);

  // A run/submit is actively in flight until it reaches a terminal result.
  const terminal =
    runResult != null &&
    runResult.status !== 'RUNNING' &&
    runResult.status !== 'PENDING';
  const inFlight = running || (runResult != null && !terminal);

  // Jump to the Result tab while a run is in flight (to show the spinner) and
  // keep it there once a terminal result lands — like LeetCode does.
  useEffect(() => {
    if (inFlight || terminal) setTab('result');
  }, [inFlight, terminal]);

  const caseCount = sampleCases.length;
  const safeActive = Math.min(activeCase, Math.max(0, caseCount - 1));

  return (
    <div className={`flex h-full flex-col ${t.panel}`}>
      <div className={`flex shrink-0 items-center gap-1 border-b px-3 ${t.border}`}>
        <TabButton active={tab === 'cases'} onClick={() => setTab('cases')} t={t}>
          Testcase
        </TabButton>
        <TabButton
          active={tab === 'result'}
          onClick={() => setTab('result')}
          t={t}
        >
          Kết quả
          {inFlight && (
            <Loader2 className="ml-1.5 inline h-3 w-3 animate-spin text-sky-500" />
          )}
        </TabButton>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        {tab === 'cases' ? (
          <CasesView
            cases={sampleCases}
            active={safeActive}
            onPick={setActiveCase}
            t={t}
          />
        ) : (
          <ResultView
            inFlight={inFlight}
            terminal={terminal}
            result={runResult}
            cases={sampleCases}
            t={t}
          />
        )}
      </div>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  t,
  children,
}: {
  active: boolean;
  onClick: () => void;
  t: CwTokens;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
        active ? t.tabActive : t.tabIdle
      }`}
    >
      {children}
    </button>
  );
}

/**
 * LeetCode-style labelled value box: a small grey label above a rounded,
 * bordered field holding the monospace value. Used for both the testcase
 * inputs and the per-case Output / Expected rows.
 */
function IOField({
  label,
  value,
  t,
  tone,
}: {
  label: string;
  value: string;
  t: CwTokens;
  tone?: 'ok' | 'bad';
}) {
  const valueCls =
    tone === 'ok' ? t.ok : tone === 'bad' ? t.bad : t.kvValue;
  return (
    <div>
      <div className={`mb-1 text-xs ${t.textMuted}`}>{label}</div>
      <div
        className={`overflow-x-auto whitespace-pre-wrap break-words rounded-lg border px-3 py-2 font-mono text-[13px] ${t.sigBox} ${valueCls}`}
      >
        {value}
      </div>
    </div>
  );
}

function CasesView({
  cases,
  active,
  onPick,
  t,
}: {
  cases: SampleTestCase[];
  active: number;
  onPick: (i: number) => void;
  t: CwTokens;
}) {
  if (cases.length === 0) {
    return (
      <p className={`text-xs ${t.textMuted}`}>
        Bài này không có testcase mẫu hiển thị — bấm “Nộp bài” để chấm trên
        bộ test ẩn.
      </p>
    );
  }
  const tc = cases[active];
  return (
    <div>
      <div className="mb-3 flex flex-wrap gap-2">
        {cases.map((c, i) => (
          <button
            key={c.id}
            type="button"
            onClick={() => onPick(i)}
            className={`rounded-lg px-3 py-1.5 text-[13px] font-medium transition-colors ${
              i === active ? t.pillActive : t.pill
            }`}
          >
            Case {i + 1}
          </button>
        ))}
      </div>
      {/* LeetCode shows ONLY the inputs here — the expected answer appears in
          the Result tab after running, so it isn't given away up front. */}
      <div className="space-y-3">
        {Object.entries(tc.inputData).map(([k, v]) => (
          <IOField key={k} label={`${k} =`} value={fmt(v)} t={t} />
        ))}
      </div>
    </div>
  );
}

function ResultView({
  inFlight,
  terminal,
  result,
  cases,
  t,
}: {
  inFlight: boolean;
  terminal: boolean;
  result: RunResultView | null;
  cases: SampleTestCase[];
  t: CwTokens;
}) {
  // Still running (or polling an unfinished submission): show only a spinner,
  // never a half-computed "0/0" verdict.
  if (inFlight || !terminal) {
    if (inFlight) {
      return (
        <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
          <Loader2 className="h-6 w-6 animate-spin text-sky-500" />
          <span className={`text-sm ${t.textBody}`}>
            Đang chạy trên {cases.length} testcase mẫu…
          </span>
        </div>
      );
    }
    return (
      <div className="flex h-full items-center justify-center px-4 text-center">
        <p className={`text-xs ${t.textMuted}`}>
          Bấm <span className={`font-semibold ${t.textBody}`}>Chạy</span> (hoặc
          ⌘/Ctrl + ↵) để chạy thử trên testcase mẫu.
        </p>
      </div>
    );
  }

  // From here on `result` is a terminal RunResultView.
  if (!result) return null;

  if (result.compileError) {
    return (
      <div>
        <div className={`mb-2 flex items-center gap-2 text-sm font-semibold ${t.bad}`}>
          <AlertTriangle className="h-4 w-4" />
          Lỗi biên dịch
        </div>
        <pre
          className={`overflow-x-auto rounded-lg px-3 py-2.5 text-[13px] leading-relaxed ${
            t.dark ? 'bg-zinc-900 text-rose-300' : 'bg-rose-50 text-rose-700'
          }`}
        >
          {result.compileError}
        </pre>
      </div>
    );
  }

  const passed = result.cases.filter((c) => c.status === 'PASSED').length;
  const total = result.cases.length;
  const allPass = total > 0 && passed === total;

  return (
    <div>
      <div className={`mb-3 text-base font-bold ${allPass ? t.ok : t.bad}`}>
        {allPass ? 'Accepted' : 'Sai kết quả'}{' '}
        <span className={`text-sm font-normal ${t.textMuted}`}>
          · {passed}/{total} testcase
        </span>
      </div>
      <div className="space-y-3">
        {result.cases.map((c) => {
          const ok = c.status === 'PASSED';
          return (
            <div
              key={c.testCaseId || c.index}
              className={`rounded-lg border p-3 ${t.card}`}
            >
              <div className="mb-2.5 flex items-center justify-between">
                <span className="flex items-center gap-1.5 text-sm font-semibold">
                  {ok ? (
                    <CheckCircle2 className={`h-4 w-4 ${t.ok}`} />
                  ) : (
                    <XCircle className={`h-4 w-4 ${t.bad}`} />
                  )}
                  <span className={ok ? t.ok : t.bad}>
                    Case {c.index + 1} · {c.status}
                  </span>
                </span>
                <span className={`text-xs ${t.textMuted}`}>
                  {c.runtimeMs != null ? `${c.runtimeMs} ms` : '—'}
                  {c.memoryKb != null
                    ? ` · ${(c.memoryKb / 1024).toFixed(1)} MB`
                    : ''}
                </span>
              </div>
              <div className="space-y-2.5">
                <IOField
                  label="Output"
                  value={c.stdout ?? '—'}
                  t={t}
                  tone={ok ? 'ok' : 'bad'}
                />
                <IOField label="Kết quả mong đợi" value={c.expected ?? '—'} t={t} />
              </div>
              {c.stderr && (
                <pre
                  className={`mt-2.5 overflow-x-auto rounded px-2.5 py-1.5 text-[12px] ${
                    t.dark
                      ? 'bg-rose-950/40 text-rose-300'
                      : 'bg-rose-50 text-rose-700'
                  }`}
                >
                  {c.stderr}
                </pre>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
