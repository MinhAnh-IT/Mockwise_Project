/**
 * Floating feedback widget shown only on the homepage. A round button pinned to
 * the bottom-right opens a modal where any visitor (logged in or not) can rate
 * the product, pick a category, and leave a message. Submits to the public POST
 * /feedbacks endpoint. Other pages don't surface the widget; admins read the
 * submissions from their own console.
 */
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  CheckCircle2,
  Loader2,
  MessageSquarePlus,
  Star,
  X,
} from 'lucide-react';
import { submitFeedback } from '@/api/feedback';
import {
  FEEDBACK_CATEGORY_LABEL,
  type FeedbackCategory,
} from '@/types/feedback';

const CATEGORIES: FeedbackCategory[] = ['GENERAL', 'FEATURE', 'BUG'];

export default function FeedbackWidget() {
  const location = useLocation();
  const [open, setOpen] = useState(false);

  // Only surface the feedback widget on the homepage.
  if (location.pathname !== '/' && location.pathname !== '/home') {
    return null;
  }

  return (
    <>
      {!open && (
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-label="Gửi góp ý"
          className="fixed bottom-5 right-5 z-40 flex items-center gap-2 rounded-full bg-secondary px-4 py-3 text-sm font-semibold text-on-secondary shadow-lg transition hover:opacity-90"
        >
          <MessageSquarePlus className="h-5 w-5" />
          <span className="hidden sm:inline">Góp ý</span>
        </button>
      )}
      {open && <FeedbackModal onClose={() => setOpen(false)} />}
    </>
  );
}

function FeedbackModal({ onClose }: { onClose: () => void }) {
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState(0);
  const [category, setCategory] = useState<FeedbackCategory>('GENERAL');
  const [content, setContent] = useState('');
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  // Close on Escape for keyboard users.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const canSubmit = rating >= 1 && content.trim().length > 0 && !submitting;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      await submitFeedback({
        rating,
        category,
        content: content.trim(),
        contactEmail: email.trim() || undefined,
      });
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Gửi góp ý thất bại, vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center"
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-2xl bg-surface-container-lowest p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-bold text-on-surface">Gửi góp ý</h3>
            <p className="text-xs text-on-surface-variant">
              Cảm nhận của bạn giúp chúng tôi cải thiện sản phẩm.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-on-surface-variant hover:bg-surface-container"
            aria-label="Đóng"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {done ? (
          <div className="flex flex-col items-center gap-3 py-8 text-center">
            <CheckCircle2 className="h-12 w-12 text-emerald-600" />
            <p className="text-sm font-semibold text-on-surface">Cảm ơn bạn đã góp ý!</p>
            <p className="text-xs text-on-surface-variant">
              Chúng tôi đã ghi nhận và sẽ xem xét sớm nhất.
            </p>
            <button
              type="button"
              onClick={onClose}
              className="mt-2 rounded-xl bg-secondary px-4 py-2 text-sm font-semibold text-on-secondary hover:opacity-90"
            >
              Đóng
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {/* Rating */}
            <div>
              <label className="mb-1.5 block text-sm font-semibold text-on-surface">
                Đánh giá trải nghiệm <span className="text-red-600">*</span>
              </label>
              <div className="flex items-center gap-1">
                {[1, 2, 3, 4, 5].map((star) => {
                  const active = star <= (hover || rating);
                  return (
                    <button
                      key={star}
                      type="button"
                      onClick={() => setRating(star)}
                      onMouseEnter={() => setHover(star)}
                      onMouseLeave={() => setHover(0)}
                      aria-label={`${star} sao`}
                      className="p-0.5"
                    >
                      <Star
                        className={`h-7 w-7 transition ${
                          active ? 'fill-amber-400 text-amber-400' : 'text-outline-variant'
                        }`}
                      />
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Category */}
            <div>
              <label className="mb-1.5 block text-sm font-semibold text-on-surface">
                Loại góp ý
              </label>
              <div className="flex flex-wrap gap-2">
                {CATEGORIES.map((c) => {
                  const on = category === c;
                  return (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setCategory(c)}
                      className={`rounded-full border px-3 py-1.5 text-xs font-medium transition ${
                        on
                          ? 'border-secondary bg-secondary text-on-secondary'
                          : 'border-outline-variant bg-surface-container-lowest text-on-surface-variant hover:border-secondary'
                      }`}
                    >
                      {FEEDBACK_CATEGORY_LABEL[c]}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Content */}
            <div>
              <label className="mb-1.5 block text-sm font-semibold text-on-surface">
                Nội dung <span className="text-red-600">*</span>
              </label>
              <textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                maxLength={2000}
                rows={4}
                placeholder="Chia sẻ trải nghiệm, lỗi gặp phải hoặc tính năng bạn mong muốn…"
                className="w-full resize-y rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface outline-none transition focus:border-secondary focus:ring-2 focus:ring-secondary/30"
              />
              <p className="mt-1 text-right text-xs text-on-surface-variant">
                {content.length}/2000
              </p>
            </div>

            {/* Optional email */}
            <div>
              <label className="mb-1.5 block text-sm font-semibold text-on-surface">
                Email liên hệ <span className="text-on-surface-variant">(tùy chọn)</span>
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="email@example.com"
                className="w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface outline-none transition focus:border-secondary focus:ring-2 focus:ring-secondary/30"
              />
              <p className="mt-1 text-xs text-on-surface-variant">
                Để lại email nếu bạn muốn chúng tôi phản hồi.
              </p>
            </div>

            {error && (
              <p className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
                {error}
              </p>
            )}

            <button
              type="button"
              onClick={handleSubmit}
              disabled={!canSubmit}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-secondary px-4 py-2.5 text-sm font-semibold text-on-secondary transition hover:opacity-90 disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
              Gửi góp ý
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
