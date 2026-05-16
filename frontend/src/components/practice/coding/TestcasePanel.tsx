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

  // Jump to the result tab the moment a run finishes, like LeetCode does.
  useEffect(() => {
    if (running) setTab('result');
    else if (runResult) setTab('result');
  }, [running, runResult]);

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
          {running && (
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
            running={running}
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
            className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
              i === active ? t.pillActive : t.pill
            }`}
          >
            Case {i + 1}
          </button>
        ))}
      </div>
      <div className="space-y-2">
        {Object.entries(tc.inputData).map(([k, v]) => (
          <KV key={k} label={k} value={fmt(v)} t={t} />
        ))}
        <KV
          label="Kết quả mong đợi"
          value={Object.values(tc.expectedOutput).map(fmt).join(', ')}
          t={t}
          muted
        />
      </div>
    </div>
  );
}

function ResultView({
  running,
  result,
  cases,
  t,
}: {
  running: boolean;
  result: RunResultView | null;
  cases: SampleTestCase[];
  t: CwTokens;
}) {
  if (running && !result) {
    return (
      <div className={`flex items-center gap-2 text-sm ${t.textBody}`}>
        <Loader2 className="h-4 w-4 animate-spin text-sky-500" />
        Đang chạy trên {cases.length} testcase mẫu…
      </div>
    );
  }
  if (!result) {
    return (
      <p className={`text-xs ${t.textMuted}`}>
        Bấm <span className={`font-semibold ${t.textBody}`}>Chạy</span> (hoặc
        ⌘/Ctrl + ↵) để chạy thử trên testcase mẫu.
      </p>
    );
  }
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
      <div
        className={`mb-3 text-base font-bold ${allPass ? t.ok : t.bad}`}
      >
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
              <div className="mb-2 flex items-center justify-between">
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
              <KV label="Output" value={c.stdout ?? '—'} t={t} />
              <KV label="Mong đợi" value={c.expected ?? '—'} t={t} muted />
              {c.stderr && (
                <pre
                  className={`mt-2 overflow-x-auto rounded px-2.5 py-1.5 text-[12px] ${
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

function KV({
  label,
  value,
  t,
  muted,
}: {
  label: string;
  value: string;
  t: CwTokens;
  muted?: boolean;
}) {
  return (
    <div className="text-[13px]">
      <span className={t.textMuted}>{label}: </span>
      <code className={`font-mono ${muted ? t.kvMuted : t.kvValue}`}>
        {value}
      </code>
    </div>
  );
}
