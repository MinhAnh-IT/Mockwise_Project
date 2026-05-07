import { Loader2 } from 'lucide-react';

type Props = {
  /** Optional sub-text under the spinner (e.g. "đã nộp câu 2"). */
  hint?: string | null;
};

/**
 * Shown after submit while we poll GET /interviews/{sid} waiting for the
 * planner to pin the next question (or flip the session to SCORED). Backend
 * keeps per-question scores hidden during the session, so we deliberately
 * say nothing about whether the previous answer was good or bad.
 */
export default function WaitingNext({ hint }: Props) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-10 text-center">
      <Loader2 className="w-8 h-8 mx-auto mb-4 text-secondary animate-spin" />
      <h3 className="text-base font-bold text-on-surface mb-2">Đang chuẩn bị câu kế…</h3>
      <p className="text-xs text-on-surface-variant max-w-sm mx-auto leading-relaxed">
        Hệ thống đang chấm câu trả lời vừa rồi và chọn câu hỏi phù hợp tiếp theo. Việc này thường mất 10–30 giây.
      </p>
      {hint && (
        <p className="mt-4 text-[11px] text-on-surface-variant/80">{hint}</p>
      )}
    </div>
  );
}
