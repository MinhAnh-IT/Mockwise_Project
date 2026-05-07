import { LogOut } from 'lucide-react';

type Props = {
  title: string;
  /** 1-indexed sequence of the question on screen. */
  current: number;
  /** Backend's `questionBudget`. Shown as "câu N / mục tiêu ~M". */
  budget: number;
  onExit: () => void;
  exiting: boolean;
};

export default function SessionHeader({ title, current, budget, onExit, exiting }: Props) {
  const pct = Math.min(100, Math.round((current / Math.max(1, budget)) * 100));
  return (
    <div className="bg-surface-container-lowest border-b border-outline-variant">
      <div className="max-w-4xl mx-auto px-6 py-4 flex items-center gap-4">
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
        <button
          type="button"
          onClick={onExit}
          disabled={exiting}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-on-surface-variant hover:text-on-surface transition-colors disabled:opacity-50"
        >
          <LogOut className="w-3.5 h-3.5" />
          {exiting ? 'Đang kết thúc…' : 'Kết thúc sớm'}
        </button>
      </div>
    </div>
  );
}
