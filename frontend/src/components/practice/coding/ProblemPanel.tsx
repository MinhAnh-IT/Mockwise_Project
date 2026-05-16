import type { CodingProblemView } from '@/types/coding';
import Markdown from './Markdown';
import { cwTokens } from './theme';

type Props = {
  problem: CodingProblemView;
  dark: boolean;
};

/** Left pane — the problem statement, à la LeetCode's "Description" tab. */
export default function ProblemPanel({ problem, dark }: Props) {
  const t = cwTokens(dark);

  return (
    <div className={`h-full overflow-y-auto px-5 py-4 ${t.panel}`}>
      <h1 className={`mb-4 text-lg font-bold ${t.textStrong}`}>
        {problem.sequence}. {problem.title}
      </h1>

      <Markdown source={problem.description} dark={dark} />
    </div>
  );
}
