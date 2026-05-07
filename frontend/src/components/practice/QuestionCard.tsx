import type { PinnedQuestionView } from '@/types/interview';

type Props = {
  question: PinnedQuestionView;
};

const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: 'Dễ',
  MEDIUM: 'Vừa',
  HARD: 'Khó',
};

const DIFFICULTY_TONE: Record<string, string> = {
  EASY: 'bg-emerald-100 text-emerald-700',
  MEDIUM: 'bg-amber-100 text-amber-700',
  HARD: 'bg-red-100 text-red-700',
};

/**
 * Renders one pinned question. TTS playback is owned by VideoRecorder
 * (auto-plays on question change), so this card is pure text + metadata.
 */
export default function QuestionCard({ question }: Props) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6">
      <div className="flex flex-wrap items-center gap-2 mb-4">
        <span className="text-[10px] font-bold uppercase tracking-widest text-secondary bg-secondary/10 px-2 py-1 rounded-md">
          {question.topicValue.replace(/_/g, ' ')}
        </span>
        <span
          className={`text-[10px] font-bold uppercase tracking-widest px-2 py-1 rounded-md ${
            DIFFICULTY_TONE[question.difficulty] ?? 'bg-surface-container text-on-surface-variant'
          }`}
        >
          {DIFFICULTY_LABEL[question.difficulty] ?? question.difficulty}
        </span>
        {question.isFollowUp && (
          <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant bg-surface-container px-2 py-1 rounded-md">
            Câu nối tiếp
          </span>
        )}
      </div>

      <p className="text-base md:text-lg text-on-surface leading-relaxed font-medium">
        {question.text}
      </p>

      {question.expectedPoints && question.expectedPoints.length > 0 && (
        <div className="mt-4 pt-4 border-t border-outline-variant/60">
          <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-2">
            Gợi ý hướng trả lời
          </p>
          <ul className="space-y-1 text-xs text-on-surface-variant">
            {question.expectedPoints.map((point) => (
              <li key={point} className="flex items-start gap-2">
                <span className="text-secondary mt-0.5">•</span>
                <span className="leading-relaxed">{point}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
