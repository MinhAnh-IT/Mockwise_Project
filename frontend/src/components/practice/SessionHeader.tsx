import { Clock, LogOut } from 'lucide-react';

type Props = {
  title: string;
  /** 1-indexed sequence of the question on screen. */
  current: number;
  /** Backend's `questionBudget`. Shown as "câu N / mục tiêu ~M". */
  budget: number;
  /**
   * Whole-session time remaining (seconds). One clock for the whole
   * interview — there is no per-question limit. Null = unknown.
   */
  secondsLeft: number | null;
  onExit: () => void;
  exiting: boolean;
};

function fmt(secondsLeft: number | null): string {
  if (secondsLeft == null) return '--:--';
  const m = Math.floor(secondsLeft / 60);
  const s = secondsLeft % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export default function SessionHeader({
  title,
  current,
  budget,
  secondsLeft,
  onExit,
  exiting,
}: Props) {
  const pct = Math.min(100, Math.round((current / Math.max(1, budget)) * 100));
  const lowTime = secondsLeft != null && secondsLeft <= 60;
  return (
    <div className="bg-surface-container-lowest border-b border-outline-variant">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 py-4 flex items-center gap-3 sm:gap-4">
        <div className="flex-1">
          <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1">
            {title}
          </p>
          <p className="text-sm font-bold text-on-surface">
            Câu {current} <span className="text-on-surface-variant font-normal">/ mục tiêu ~{budget} câu</span>
          </p>
          <div className="mt-2 h-1.5 rounded-full bg-surface-container overflow-hidden">
            <div
              className="h-full bg-secondary transition-all duration-500"
              style={{ width: `${pct}%` }}
            />
          </div>
        </div>
        <div
          className={`inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-bold tabular-nums ${
            lowTime
              ? 'bg-red-100 text-red-700'
              : 'bg-surface-container text-on-surface'
          }`}
          title="Thời gian còn lại của phiên phỏng vấn"
        >
          <Clock className="w-4 h-4" />
          {fmt(secondsLeft)}
        </div>
        <button
          type="button"
          onClick={onExit}
          disabled={exiting}
          className="inline-flex shrink-0 items-center gap-1.5 text-xs font-semibold text-on-surface-variant hover:text-on-surface transition-colors disabled:opacity-50"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">
            {exiting ? 'Đang kết thúc…' : 'Kết thúc sớm'}
          </span>
          <span className="sm:hidden">{exiting ? '…' : 'Kết thúc'}</span>
        </button>
      </div>
    </div>
  );
}
