import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { MessageSquare, RefreshCw, Search, Star, Trash2, X } from 'lucide-react';
import { ApiError } from '@/api/client';
import { useUrlState } from '@/lib/useUrlState';
import {
  deleteFeedback,
  getFeedbackStats,
  listFeedback,
  updateFeedbackStatus,
} from '@/api/adminFeedback';
import {
  AdminShell,
  Button,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Input,
  Pill,
  Select,
  Spinner,
  Textarea,
  Toast,
  type ToastState,
} from '@/components/admin/ui';
import type { PageResult } from '@/types/profile';
import {
  FEEDBACK_CATEGORY_LABEL,
  FEEDBACK_STATUS_LABEL,
  type Feedback,
  type FeedbackCategory,
  type FeedbackStats,
  type FeedbackStatus,
} from '@/types/feedback';

const PAGE_SIZE = 10;

const STATUS_OPTIONS: FeedbackStatus[] = ['NEW', 'REVIEWED', 'RESOLVED'];
const CATEGORY_OPTIONS: FeedbackCategory[] = ['BUG', 'FEATURE', 'GENERAL'];

const STATUS_TONE: Record<FeedbackStatus, 'secondary' | 'amber' | 'emerald'> = {
  NEW: 'secondary',
  REVIEWED: 'amber',
  RESOLVED: 'emerald',
};

const CATEGORY_TONE: Record<FeedbackCategory, 'red' | 'secondary' | 'neutral'> = {
  BUG: 'red',
  FEATURE: 'secondary',
  GENERAL: 'neutral',
};

const DATETIME_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

function fmtTime(iso: string | null): string {
  return iso ? DATETIME_FMT.format(new Date(iso)) : '—';
}

function Stars({ value }: { value: number }) {
  return (
    <span className="inline-flex items-center gap-0.5" title={`${value}/5`}>
      {[1, 2, 3, 4, 5].map((s) => (
        <Star
          key={s}
          className={`h-3.5 w-3.5 ${
            s <= value ? 'fill-amber-400 text-amber-400' : 'text-outline-variant'
          }`}
        />
      ))}
    </span>
  );
}

type DraftFilters = {
  status: string;
  category: string;
  rating: string;
  keyword: string;
};

const EMPTY_FILTERS: DraftFilters = { status: '', category: '', rating: '', keyword: '' };

// Committed filters + page index persisted to the URL so they survive a refresh.
const URL_DEFAULTS = { ...EMPTY_FILTERS, page: 0 };

export default function AdminFeedbackPage() {
  const [data, setData] = useState<PageResult<Feedback> | null>(null);
  const [stats, setStats] = useState<FeedbackStats | null>(null);
  const [urlState, setUrlState] = useUrlState(URL_DEFAULTS);
  const page = urlState.page;
  const applied: DraftFilters = useMemo(
    () => ({
      status: urlState.status,
      category: urlState.category,
      rating: urlState.rating,
      keyword: urlState.keyword,
    }),
    [urlState.status, urlState.category, urlState.rating, urlState.keyword],
  );
  const [draft, setDraft] = useState<DraftFilters>(() => ({ ...applied }));

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Feedback | null>(null);
  const [toRemove, setToRemove] = useState<Feedback | null>(null);
  const [removing, setRemoving] = useState(false);
  const [toast, setToast] = useState<ToastState>(null);
  const firstLoad = useRef(true);

  const load = useCallback(async () => {
    if (firstLoad.current) setLoading(true);
    else setRefreshing(true);
    try {
      const [list, overview] = await Promise.all([
        listFeedback(
          {
            status: (applied.status || undefined) as FeedbackStatus | undefined,
            category: (applied.category || undefined) as FeedbackCategory | undefined,
            rating: applied.rating ? Number(applied.rating) : undefined,
            keyword: applied.keyword.trim() || undefined,
          },
          page,
          PAGE_SIZE,
        ),
        getFeedbackStats(),
      ]);
      setData(list);
      setStats(overview);
      setError(null);
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) {
        setError(err instanceof Error ? err.message : 'Không tải được góp ý.');
      }
    } finally {
      firstLoad.current = false;
      setLoading(false);
      setRefreshing(false);
    }
  }, [applied, page]);

  useEffect(() => {
    load();
  }, [load]);

  const applyFilters = () => setUrlState({ ...draft, page: 0 });

  const resetFilters = () => {
    setDraft(EMPTY_FILTERS);
    setUrlState({ ...EMPTY_FILTERS, page: 0 });
  };

  const handleRemove = async () => {
    if (!toRemove) return;
    setRemoving(true);
    try {
      await deleteFeedback(toRemove.id);
      setToRemove(null);
      setToast({ kind: 'success', text: 'Đã xoá góp ý.' });
      load();
    } catch (err) {
      setToast({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Xoá thất bại.',
      });
    } finally {
      setRemoving(false);
    }
  };

  return (
    <AdminShell
      title="Ý kiến người dùng"
      subtitle="Tiếp nhận góp ý, báo lỗi và đề xuất tính năng từ người dùng."
      breadcrumb={[{ label: 'Tổng quan', to: '/admin' }, { label: 'Ý kiến người dùng' }]}
      actions={
        <Button variant="outline" loading={refreshing} onClick={load}>
          <RefreshCw className="h-4 w-4" />
          Làm mới
        </Button>
      }
    >
      {loading ? (
        <Spinner label="Đang tải góp ý…" />
      ) : error && !data ? (
        <ErrorState message={error} onRetry={load} />
      ) : (
        <div className="space-y-6">
          {error && (
            <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-800">
              Lần tải gần nhất lỗi: {error}. Đang hiển thị dữ liệu cũ.
            </p>
          )}

          {stats && <StatsCards stats={stats} />}

          {/* Filters */}
          <section className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Labeled label="Trạng thái">
                <Select
                  value={draft.status}
                  onChange={(e) => setDraft((d) => ({ ...d, status: e.target.value }))}
                >
                  <option value="">Mọi trạng thái</option>
                  {STATUS_OPTIONS.map((s) => (
                    <option key={s} value={s}>
                      {FEEDBACK_STATUS_LABEL[s]}
                    </option>
                  ))}
                </Select>
              </Labeled>
              <Labeled label="Loại">
                <Select
                  value={draft.category}
                  onChange={(e) => setDraft((d) => ({ ...d, category: e.target.value }))}
                >
                  <option value="">Mọi loại</option>
                  {CATEGORY_OPTIONS.map((c) => (
                    <option key={c} value={c}>
                      {FEEDBACK_CATEGORY_LABEL[c]}
                    </option>
                  ))}
                </Select>
              </Labeled>
              <Labeled label="Số sao">
                <Select
                  value={draft.rating}
                  onChange={(e) => setDraft((d) => ({ ...d, rating: e.target.value }))}
                >
                  <option value="">Mọi mức</option>
                  {[5, 4, 3, 2, 1].map((r) => (
                    <option key={r} value={r}>
                      {r} sao
                    </option>
                  ))}
                </Select>
              </Labeled>
              <Labeled label="Tìm kiếm">
                <Input
                  value={draft.keyword}
                  onChange={(e) => setDraft((d) => ({ ...d, keyword: e.target.value }))}
                  placeholder="Nội dung hoặc email"
                  onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
                />
              </Labeled>
            </div>
            <div className="mt-3 flex items-center gap-2">
              <Button onClick={applyFilters}>
                <Search className="h-4 w-4" />
                Lọc
              </Button>
              <Button variant="outline" onClick={resetFilters}>
                Xoá lọc
              </Button>
            </div>
          </section>

          {/* Table */}
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-on-surface-variant">
              <MessageSquare className="h-4 w-4" />
              Danh sách góp ý
              {data && <span className="font-normal opacity-70">({data.totalElements})</span>}
            </h2>
            {data && data.items.length > 0 ? (
              <>
                <FeedbackTable
                  items={data.items}
                  onOpen={setSelected}
                  onDelete={setToRemove}
                />
                <Pagination
                  page={data.page}
                  totalPages={data.totalPages}
                  onPrev={() => setUrlState({ page: Math.max(0, page - 1) })}
                  onNext={() =>
                    setUrlState({ page: Math.min(data.totalPages - 1, page + 1) })
                  }
                />
              </>
            ) : (
              <EmptyState message="Chưa có góp ý nào khớp bộ lọc." />
            )}
          </section>
        </div>
      )}

      {selected && (
        <FeedbackDetailModal
          feedback={selected}
          onClose={() => setSelected(null)}
          onSaved={(updated) => {
            setSelected(null);
            setToast({ kind: 'success', text: 'Đã cập nhật trạng thái.' });
            setData((prev) =>
              prev
                ? { ...prev, items: prev.items.map((f) => (f.id === updated.id ? updated : f)) }
                : prev,
            );
            load();
          }}
          onError={(msg) => setToast({ kind: 'error', text: msg })}
        />
      )}

      <ConfirmDialog
        open={!!toRemove}
        title="Xoá góp ý"
        message="Bạn có chắc muốn xoá góp ý này? Hành động không thể hoàn tác."
        confirmText="Xoá"
        danger
        loading={removing}
        onConfirm={handleRemove}
        onCancel={() => setToRemove(null)}
      />

      <Toast toast={toast} onClose={() => setToast(null)} />
    </AdminShell>
  );
}

// ── Sub-components ───────────────────────────────────────────────────────────

function Labeled({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-semibold text-on-surface-variant">{label}</span>
      {children}
    </label>
  );
}

function StatsCards({ stats }: { stats: FeedbackStats }) {
  const cards: Array<{ label: string; value: string | number }> = [
    { label: 'Tổng góp ý', value: stats.total },
    { label: 'Điểm trung bình', value: stats.avgRating ? `${stats.avgRating} ★` : '—' },
    { label: 'Mới', value: stats.newCount },
    { label: 'Đã xử lý', value: stats.resolvedCount },
    { label: 'Báo lỗi', value: stats.bugCount },
    { label: 'Đề xuất', value: stats.featureCount },
  ];
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
      {cards.map((c) => (
        <div
          key={c.label}
          className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4"
        >
          <p className="text-xs text-on-surface-variant">{c.label}</p>
          <p className="mt-1 text-xl font-bold text-on-surface">{c.value}</p>
        </div>
      ))}
    </div>
  );
}

function FeedbackTable({
  items,
  onOpen,
  onDelete,
}: {
  items: Feedback[];
  onOpen: (f: Feedback) => void;
  onDelete: (f: Feedback) => void;
}) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-outline-variant">
      <table className="w-full text-sm">
        <thead className="bg-surface-container text-left text-xs uppercase tracking-wide text-on-surface-variant">
          <tr>
            <th className="px-3 py-2">Thời gian</th>
            <th className="px-3 py-2">Đánh giá</th>
            <th className="px-3 py-2">Loại</th>
            <th className="px-3 py-2">Nội dung</th>
            <th className="px-3 py-2">Email</th>
            <th className="px-3 py-2">Trạng thái</th>
            <th className="px-3 py-2"></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant">
          {items.map((f) => (
            <tr
              key={f.id}
              onClick={() => onOpen(f)}
              className="cursor-pointer transition hover:bg-surface-container/60"
            >
              <td className="whitespace-nowrap px-3 py-2 text-xs text-on-surface-variant">
                {fmtTime(f.createdAt)}
              </td>
              <td className="px-3 py-2">
                <Stars value={f.rating} />
              </td>
              <td className="px-3 py-2">
                <Pill tone={CATEGORY_TONE[f.category]}>{FEEDBACK_CATEGORY_LABEL[f.category]}</Pill>
              </td>
              <td className="max-w-xs px-3 py-2">
                <span className="line-clamp-2 text-on-surface">{f.content}</span>
              </td>
              <td className="px-3 py-2 text-xs text-on-surface-variant">
                {f.contactEmail ?? '—'}
              </td>
              <td className="px-3 py-2">
                <Pill tone={STATUS_TONE[f.status]}>{FEEDBACK_STATUS_LABEL[f.status]}</Pill>
              </td>
              <td className="px-3 py-2 text-right">
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(f);
                  }}
                  className="rounded-lg p-1.5 text-on-surface-variant transition hover:bg-red-50 hover:text-red-600"
                  aria-label="Xoá"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Pagination({
  page,
  totalPages,
  onPrev,
  onNext,
}: {
  page: number;
  totalPages: number;
  onPrev: () => void;
  onNext: () => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-3 flex items-center justify-end gap-2 text-sm">
      <Button variant="outline" disabled={page <= 0} onClick={onPrev}>
        Trước
      </Button>
      <span className="text-on-surface-variant">
        Trang {page + 1}/{totalPages}
      </span>
      <Button variant="outline" disabled={page >= totalPages - 1} onClick={onNext}>
        Sau
      </Button>
    </div>
  );
}

function FeedbackDetailModal({
  feedback,
  onClose,
  onSaved,
  onError,
}: {
  feedback: Feedback;
  onClose: () => void;
  onSaved: (updated: Feedback) => void;
  onError: (message: string) => void;
}) {
  const [status, setStatus] = useState<FeedbackStatus>(feedback.status);
  const [adminNote, setAdminNote] = useState(feedback.adminNote ?? '');
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    try {
      const updated = await updateFeedbackStatus(feedback.id, {
        status,
        adminNote: adminNote.trim() || undefined,
      });
      onSaved(updated);
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Cập nhật thất bại.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="max-h-[85vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-surface p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-bold text-on-surface">Chi tiết góp ý</h3>
          <button
            onClick={onClose}
            className="rounded-lg p-1 hover:bg-surface-container"
            aria-label="Đóng"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="space-y-3">
          <div className="flex items-center gap-3">
            <Stars value={feedback.rating} />
            <Pill tone={CATEGORY_TONE[feedback.category]}>
              {FEEDBACK_CATEGORY_LABEL[feedback.category]}
            </Pill>
          </div>

          <div className="rounded-xl bg-surface-container p-3 text-sm text-on-surface">
            <p className="whitespace-pre-wrap break-words">{feedback.content}</p>
          </div>

          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
            <Detail label="Gửi lúc" value={fmtTime(feedback.createdAt)} />
            <Detail label="Xử lý lúc" value={fmtTime(feedback.reviewedAt)} />
            <Detail label="Email liên hệ" value={feedback.contactEmail ?? '—'} />
            <Detail
              label="Người gửi"
              value={feedback.userId ? feedback.userId.slice(0, 8) + '…' : 'Khách vãng lai'}
            />
          </dl>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-on-surface">
              Trạng thái
            </label>
            <Select value={status} onChange={(e) => setStatus(e.target.value as FeedbackStatus)}>
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {FEEDBACK_STATUS_LABEL[s]}
                </option>
              ))}
            </Select>
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-on-surface">
              Ghi chú nội bộ
            </label>
            <Textarea
              value={adminNote}
              onChange={(e) => setAdminNote(e.target.value)}
              maxLength={1000}
              rows={3}
              placeholder="Ghi chú khi xử lý (chỉ admin thấy)…"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={onClose} disabled={saving}>
              Huỷ
            </Button>
            <Button onClick={save} loading={saving}>
              Lưu
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-on-surface-variant">{label}</dt>
      <dd className="break-words font-medium text-on-surface">{value}</dd>
    </div>
  );
}
