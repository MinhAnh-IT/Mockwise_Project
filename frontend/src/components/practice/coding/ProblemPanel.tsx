import type { CodingProblemView } from '@/types/coding';
import Markdown from './Markdown';
import { cwTokens } from './theme';

type Props = {
  problem: CodingProblemView;
  dark: boolean;
};

/** Pretty-print one test case's input/output map as `k = v, k = v`. */
function fmtMap(m: Record<string, unknown>): string {
  return Object.entries(m)
    .map(([k, v]) => `${k} = ${JSON.stringify(v)}`)
    .join(', ');
}

/**
 * Left pane — the problem statement, à la LeetCode's "Description" tab:
 * description → worked examples (from the visible sample cases) →
 * constraints. Each example shows Input / Output and, when the question
 * setter provided one, an explanation note.
 */
export default function ProblemPanel({ problem, dark }: Props) {
  const t = cwTokens(dark);
  const examples = problem.sampleTestCases ?? [];
  const constraints = problem.constraints?.trim();

  return (
    <div className={`h-full overflow-y-auto px-5 py-4 ${t.panel}`}>
      <h1 className={`mb-4 text-lg font-bold ${t.textStrong}`}>
        {problem.sequence}. {problem.title}
      </h1>

      <Markdown source={problem.description} dark={dark} />

      {examples.length > 0 && (
        <div className="mt-5 space-y-4">
          {examples.map((tc, idx) => (
            <div key={tc.id ?? idx}>
              <p className={`mb-1.5 text-sm font-semibold ${t.textStrong}`}>
                Ví dụ {idx + 1}:
              </p>
              <div
                className={`rounded-lg border px-3.5 py-2.5 text-[13px] leading-relaxed ${t.border} ${
                  dark ? 'bg-zinc-900/60' : 'bg-zinc-50'
                }`}
              >
                <p className={t.textBody}>
                  <span className={`font-semibold ${t.textStrong}`}>
                    Input:{' '}
                  </span>
                  <code className="font-mono">{fmtMap(tc.inputData)}</code>
                </p>
                <p className={`mt-1 ${t.textBody}`}>
                  <span className={`font-semibold ${t.textStrong}`}>
                    Output:{' '}
                  </span>
                  <code className="font-mono">{fmtMap(tc.expectedOutput)}</code>
                </p>
                {tc.note && tc.note.trim() && (
                  <p className={`mt-1 ${t.textBody}`}>
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
        <div className="mt-5">
          <p className={`mb-1.5 text-sm font-semibold ${t.textStrong}`}>
            Ràng buộc:
          </p>
          <Markdown source={constraints} dark={dark} />
        </div>
      )}
    </div>
  );
}
