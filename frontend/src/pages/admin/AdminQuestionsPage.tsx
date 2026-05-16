import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  deleteQuestion,
  listBehavioral,
  listCoding,
  listCore,
  updateStatus,
  type ListPage,
} from '@/api/questionBank';
import {
  AdminShell,
  Button,
  ConfirmDialog,
  DifficultyPill,
  EmptyState,
  EnumSelect,
  ErrorState,
  Pill,
  Select,
  Spinner,
  StatusPill,
  TagInput,
  Toast,
  type ToastState,
} from '@/components/admin/ui';
import {
  COMPETENCIES,
  COMPETENCY_LABEL,
  DIFFICULTIES,
  DIFFICULTY_LABEL,
  DOMAINS,
  DOMAIN_LABEL,
  KIND_LABEL,
  STATUSES,
  STATUS_LABEL,
  TARGET_ROLES,
  TARGET_ROLE_LABEL,
  type AnyQuestion,
  type BehavioralFilters,
  type BehavioralQuestion,
  type CodingFilters,
  type CodingQuestion,
  type Competency,
  type CoreFilters,
  type CoreQuestion,
  type Difficulty,
  type Domain,
  type QuestionKind,
  type QuestionStatus,
  type TargetRole,
} from '@/types/questionBank';

const PAGE_SIZE = 20;

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const TABS: QuestionKind[] = ['behavioral', 'core', 'coding'];

type AnyFilters = BehavioralFilters & CoreFilters & CodingFilters;

const EMPTY_FILTERS: AnyFilters = {};

export default function AdminQuestionsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [kind, setKind] = useState<QuestionKind>(
    () =>
      (location.state as { kind?: QuestionKind } | null)?.kind ?? 'behavioral',
  );
  const [filters, setFilters] = useState<AnyFilters>(EMPTY_FILTERS);
  const [items, setItems] = useState<AnyQuestion[]>([]);
  const [totalCount, setTotalCount] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState>(null);
  const [pendingDelete, setPendingDelete] = useState<AnyQuestion | null>(null);
  const [deleting, setDeleting] = useState(false);
  // bumping this re-runs the fetch effect (Apply filters / retry)
  const [reloadKey, setReloadKey] = useState(0);
  const flashShownRef = useRef(false);

  const fetchPage = useCallback(
    (p: number): Promise<ListPage<AnyQuestion>> => {
      if (kind === 'behavioral')
        return listBehavioral(filters, p, PAGE_SIZE) as Promise<
          ListPage<AnyQuestion>
        >;
      if (kind === 'core')
        return listCore(filters, p, PAGE_SIZE) as Promise<
          ListPage<AnyQuestion>
        >;
      return listCoding(filters, p, PAGE_SIZE) as Promise<
        ListPage<AnyQuestion>
      >;
    },
    [kind, filters],
  );

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetchPage(0)
      .then((res) => {
        if (cancelled) return;
        setItems(res.items);
        setTotalCount(res.totalCount);
        setPage(0);
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) return;
        setError(
          err instanceof Error ? err.message : 'Không tải được danh sách.',
        );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [fetchPage, reloadKey]);

  // Surface the success message a form page handed off via navigation state,
  // then strip it so a refresh/back doesn't replay the toast.
  useEffect(() => {
    const flash = (location.state as { flash?: string } | null)?.flash;
    if (flash && !flashShownRef.current) {
      flashShownRef.current = true;
      setToast({ kind: 'success', text: flash });
      navigate(location.pathname, { replace: true });
    }
  }, [location.state, location.pathname, navigate]);

  const hasNext = totalCount !== null && items.length < totalCount;

  const onLoadMore = async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    try {
      const next = await fetchPage(page + 1);
      setItems((prev) => [...prev, ...next.items]);
      setPage((p) => p + 1);
      if (next.totalCount !== null) setTotalCount(next.totalCount);
    } catch (err) {
      setToast({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Không tải thêm được.',
      });
    } finally {
      setLoadingMore(false);
    }
  };

  const switchKind = (k: QuestionKind) => {
    if (k === kind) return;
    setKind(k);
    setFilters(EMPTY_FILTERS);
    setItems([]);
    setTotalCount(null);
    setPage(0);
  };

  const applyFilters = () => setReloadKey((k) => k + 1);
  const clearFilters = () => {
    setFilters(EMPTY_FILTERS);
    setReloadKey((k) => k + 1);
  };

  const onChangeStatus = async (q: AnyQuestion, status: QuestionStatus) => {
    const prev = q.status;
    setItems((list) =>
      list.map((it) => (it.id === q.id ? { ...it, status } : it)),
    );
    try {
      await updateStatus(q.id, status);
      setToast({ kind: 'success', text: 'Đã cập nhật trạng thái.' });
    } catch (err) {
      setItems((list) =>
        list.map((it) => (it.id === q.id ? { ...it, status: prev } : it)),
      );
      setToast({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Đổi trạng thái thất bại.',
      });
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await deleteQuestion(pendingDelete.id);
      setItems((list) => list.filter((it) => it.id !== pendingDelete.id));
      setTotalCount((c) => (c === null ? c : Math.max(0, c - 1)));
      setToast({ kind: 'success', text: 'Đã xoá câu hỏi.' });
      setPendingDelete(null);
    } catch (err) {
      setToast({
        kind: 'error',
        text: err instanceof Error ? err.message : 'Xoá thất bại.',
      });
    } finally {
      setDeleting(false);
    }
  };

  const goEdit = (q: AnyQuestion) =>
    navigate(`/admin/questions/${kind}/${q.id}/edit`, {
      state: { record: q },
    });

  return (
    <AdminShell
      title="Ngân hàng câu hỏi"
      subtitle="Quản lý câu hỏi phỏng vấn theo từng loại."
      breadcrumb={[
        { label: 'Tổng quan', to: '/admin' },
        { label: 'Ngân hàng câu hỏi' },
      ]}
      actions={
        <Button onClick={() => navigate(`/admin/questions/new/${kind}`)}>
          <Plus className="h-4 w-4" />
          Tạo câu hỏi {KIND_LABEL[kind]}
        </Button>
      }
    >
      <Toast toast={toast} onClose={() => setToast(null)} />

      {/* Tabs */}
      <div className="mb-6 flex gap-1 border-b border-outline-variant">
        {TABS.map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => switchKind(t)}
            className={`-mb-px border-b-2 px-4 py-2.5 text-sm font-semibold transition ${
              t === kind
                ? 'border-secondary text-secondary'
                : 'border-transparent text-on-surface-variant hover:text-on-surface'
            }`}
          >
            {KIND_LABEL[t]}
          </button>
        ))}
      </div>

      {/* Filters */}
      <div className="mb-6 grid grid-cols-1 gap-3 rounded-2xl border border-outline-variant bg-surface-container-lowest p-4 sm:grid-cols-2 lg:grid-cols-4">
        {kind === 'behavioral' && (
          <EnumSelect<Competency>
            value={filters.competency}
            onChange={(v) =>
              setFilters((f) => ({ ...f, competency: v || undefined }))
            }
            options={COMPETENCIES}
            labels={COMPETENCY_LABEL}
            placeholder="Mọi năng lực"
          />
        )}
        {kind === 'core' && (
          <>
            <EnumSelect<Domain>
              value={filters.domain}
              onChange={(v) =>
                setFilters((f) => ({ ...f, domain: v || undefined }))
              }
              options={DOMAINS}
              labels={DOMAIN_LABEL}
              placeholder="Mọi lĩnh vực"
            />
            <EnumSelect<TargetRole>
              value={filters.targetRole}
              onChange={(v) =>
                setFilters((f) => ({ ...f, targetRole: v || undefined }))
              }
              options={TARGET_ROLES}
              labels={TARGET_ROLE_LABEL}
              placeholder="Mọi vị trí"
            />
          </>
        )}
        <EnumSelect<Difficulty>
          value={filters.difficulty}
          onChange={(v) =>
            setFilters((f) => ({ ...f, difficulty: v || undefined }))
          }
          options={DIFFICULTIES}
          labels={DIFFICULTY_LABEL}
          placeholder="Mọi độ khó"
        />
        <EnumSelect<QuestionStatus>
          value={filters.status}
          onChange={(v) =>
            setFilters((f) => ({ ...f, status: v || undefined }))
          }
          options={STATUSES}
          labels={STATUS_LABEL}
          placeholder="Mọi trạng thái"
        />
        <div className="sm:col-span-2 lg:col-span-1">
          <TagInput
            value={filters.tags ?? []}
            onChange={(tags) =>
              setFilters((f) => ({ ...f, tags: tags.length ? tags : undefined }))
            }
            placeholder="Lọc theo tag…"
          />
        </div>
        <div className="flex items-center gap-2 sm:col-span-2 lg:col-span-4">
          <Button onClick={applyFilters}>Áp dụng lọc</Button>
          <Button variant="ghost" onClick={clearFilters}>
            Xoá lọc
          </Button>
          {totalCount !== null && (
            <span className="ml-auto text-sm text-on-surface-variant">
              {totalCount} câu hỏi
            </span>
          )}
        </div>
      </div>

      {/* List */}
      {loading ? (
        <Spinner />
      ) : error ? (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      ) : items.length === 0 ? (
        <EmptyState message="Chưa có câu hỏi nào khớp bộ lọc." />
      ) : (
        <div className="space-y-3">
          {items.map((q) => (
            <QuestionCard
              key={q.id}
              kind={kind}
              q={q}
              onEdit={() => goEdit(q)}
              onDelete={() => setPendingDelete(q)}
              onStatus={(s) => onChangeStatus(q, s)}
            />
          ))}
          {hasNext && (
            <div className="pt-2 text-center">
              <Button
                variant="outline"
                loading={loadingMore}
                onClick={onLoadMore}
              >
                Tải thêm
              </Button>
            </div>
          )}
        </div>
      )}

      <ConfirmDialog
        open={!!pendingDelete}
        title="Xoá câu hỏi?"
        message="Hành động này không thể hoàn tác. Câu hỏi sẽ bị xoá vĩnh viễn khỏi ngân hàng."
        confirmText="Xoá"
        danger
        loading={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </AdminShell>
  );
}

function QuestionCard({
  kind,
  q,
  onEdit,
  onDelete,
  onStatus,
}: {
  kind: QuestionKind;
  q: AnyQuestion;
  onEdit: () => void;
  onDelete: () => void;
  onStatus: (s: QuestionStatus) => void;
}) {
  const title =
    kind === 'coding'
      ? (q as CodingQuestion).title
      : (q as BehavioralQuestion | CoreQuestion).text;

  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4 transition hover:border-secondary/40">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <p className="line-clamp-2 font-semibold text-on-surface">{title}</p>

          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            <DifficultyPill difficulty={q.difficulty} />
            <StatusPill status={q.status} />
            {kind === 'behavioral' && (
              <Pill tone="secondary">
                {COMPETENCY_LABEL[(q as BehavioralQuestion).competency]}
              </Pill>
            )}
            {kind === 'core' && (
              <>
                <Pill tone="secondary">
                  {DOMAIN_LABEL[(q as CoreQuestion).domain]}
                </Pill>
                {(q as CoreQuestion).targetRoles?.slice(0, 3).map((r) => (
                  <Pill key={r}>{TARGET_ROLE_LABEL[r]}</Pill>
                ))}
              </>
            )}
            {kind === 'coding' && (
              <>
                <Pill>{(q as CodingQuestion).timeLimitMinutes} phút</Pill>
                <Pill>
                  {(q as CodingQuestion).testCases?.length ?? 0} test case
                </Pill>
              </>
            )}
          </div>

          {q.tags?.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {q.tags.map((t) => (
                <span
                  key={t}
                  className="rounded-md bg-surface-container px-2 py-0.5 text-xs text-on-surface-variant"
                >
                  #{t}
                </span>
              ))}
            </div>
          )}

          <p className="mt-2 text-xs text-on-surface-variant">
            Cập nhật {DATE_FMT.format(new Date(q.updatedAt))}
          </p>
        </div>

        <div className="flex shrink-0 flex-col items-end gap-2">
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={onEdit}
              className="rounded-lg p-2 text-on-surface-variant transition hover:bg-surface-container-low hover:text-secondary"
              aria-label="Sửa"
              title="Sửa"
            >
              <Pencil className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={onDelete}
              className="rounded-lg p-2 text-on-surface-variant transition hover:bg-red-50 hover:text-red-600"
              aria-label="Xoá"
              title="Xoá"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
          <Select
            value={q.status}
            onChange={(e) => onStatus(e.target.value as QuestionStatus)}
            className="!w-auto !py-1 text-xs"
            aria-label="Đổi trạng thái"
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </Select>
        </div>
      </div>
    </div>
  );
}
