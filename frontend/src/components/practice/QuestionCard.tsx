import type { PinnedQuestionView } from '@/types/interview';

type Props = {
  question: PinnedQuestionView;
};

/**
 * Renders one pinned question. TTS playback is owned by VideoRecorder
 * (auto-plays on question change), so this card is pure text. Topic /
 * difficulty / expected points / follow-up classification are intentionally
 * not shown here — the backend redacts them while the session is in flight
 * so the candidate can't read the rubric off the question card. The audio
 * itself conveys whether a question follows up on the previous one.
 */
export default function QuestionCard({ question }: Props) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6">
      <p className="text-base md:text-lg text-on-surface leading-relaxed font-medium">
        {question.text}
      </p>
    </div>
  );
}
