import { useNavigate } from 'react-router-dom';
import CodingWorkspace from '@/components/practice/coding/CodingWorkspace';
import type { PinnedQuestionView } from '@/types/interview';

/**
 * Design-review only — lets you see the LeetCode-style workspace at
 * /practice/coding/preview without a running backend. `getCodingProblem`
 * 404s here so the workspace falls back to its built-in demo problem.
 * DELETE this page + its route once the BE contract ships.
 */
const PREVIEW_QUESTION: PinnedQuestionView = {
  sessionQuestionId: 'preview-sq',
  sequence: 1,
  questionId: null,
  questionType: 'LIVE_CODING',
  topicKind: null,
  topicValue: null,
  difficulty: 'EASY',
  source: null,
  isFollowUp: null,
  parentSessionQuestionId: null,
  text: '',
  audioKey: null,
  audioUrl: null,
  expectedPoints: null,
  latestAnswerId: null,
  answer: null,
};

export default function PracticeCodingPreviewPage() {
  const navigate = useNavigate();
  return (
    <CodingWorkspace
      sessionId="preview-session"
      question={PREVIEW_QUESTION}
      budget={1}
      busy={false}
      previewMode
      onSubmit={(code, language) => {
        // eslint-disable-next-line no-alert
        window.alert(
          `Demo — đã "nộp" ${language}, ${code.length} ký tự. ` +
            'Luồng nộp thật chạy qua PracticeSessionPage.',
        );
      }}
      onExit={() => navigate('/practice')}
    />
  );
}
