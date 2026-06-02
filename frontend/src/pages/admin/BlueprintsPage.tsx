import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Pencil, Plus, Star, Trash2 } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  deleteBlueprint,
  listBlueprints,
  setBlueprintDefault,
  type ListPage,
} from '@/api/blueprint';
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
  Toast,
  type ToastState,
} from '@/components/admin/ui';
import {
  BLUEPRINT_ROLES,
  BLUEPRINT_ROLE_LABEL,
  INTERVIEW_TYPES,
  INTERVIEW_TYPE_LABEL,
  IMPORTANCE_LABEL,
  LEVELS,
  LEVEL_LABEL,
  type Blueprint,
  type BlueprintFilters,
  type BlueprintRole,
  type InterviewType,
  type Level,
} from '@/types/blueprint';
import {
  COMPETENCY_LABEL,
  DIFFICULTY_LABEL,
  DOMAIN_LABEL,
} from '@/types/questionBank';

const PAGE_SIZE = 20;

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const EMPTY_FILTERS: BlueprintFilters = {};

export default function BlueprintsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [filters, setFilters] = useState<BlueprintFilters>(EMPTY_FILTERS);
  const [items, setItems] = useState<Blueprint[]>([]);
  const [totalCount, setTotalCount] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState>(null);
  const [pendingDelete, setPendingDelete] = useState<Blueprint | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [settingDefault, setSettingDefault] = useState<string | null>(null);
  // bumping this re-runs the fetch effect (Apply filters / retry / refetch)
  const [reloadKey, setReloadKey] = useState(0);
  const flashShownRef = useRef(false);

  const fetchPage = useCallback(
    (p: number): Promise<ListPage<Blueprint>> =>
      listBlueprints(filters, p, PAGE_SIZE),
    [filters],
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
        setError(err instanceof Error ? err.message : 'Không tải được danh sách.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [fetchPage, reloadKey]);

  // Surface the success message a form page handed off, then strip it so a
  // refresh/back doesn't replay the toast.
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

  const applyFilters = () => setReloadKey((k) => k + 1);
  const clearFilters = () => {
    setFilters(EMPTY_FILTERS);
    setReloadKey((k) => k + 1);
  };

  const onSetDefault = async (b: Blueprint) => {
    setSettingDefault(b.id);
    try {
      await setBlueprintDefault(b.id, true);
      setToast({ kind: 'success', text: 'Đã đặt làm mặc định.' });
      // Server cleared the sibling default — refetch to reflect it accurately.
      setReloadKey((k) => k + 1);
    } catch (err) {
      setToast({
        kind: 'error',
        text: err instanceof ApiError ? err.message : 'Đặt mặc định thất bại.',
      });
    } finally {
      setSettingDefault(null);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await deleteBlueprint(pendingDelete.id);
      setItems((list) => list.filter((it) => it.id !== pendingDelete.id));
      setTotalCount((c) => (c === null ? c : Math.max(0, c - 1)));
      setToast({ kind: 'success', text: 'Đã xoá blueprint.' });
      setPendingDelete(null);
    } catch (err) {
      // 4097 BLUEPRINT_IN_USE → keep the row, show the server message.
      setToast({
        kind: 'error',
        text: err instanceof ApiError ? err.message : 'Xoá thất bại.',
      });
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  };

  const goEdit = (b: Blueprint) =>
    navigate(`/admin/blueprints/${b.id}/edit`, { state: { record: b } });

  // isDefault filter is a tri-state Select (all / only default / not default).
  const isDefaultValue =
    filters.isDefault === undefined ? '' : String(filters.isDefault);
  const onIsDefaultChange = (raw: string) =>
    setFilters((f) => ({
      ...f,
      isDefault: raw === '' ? undefined : raw === 'true',
    }));

  return (
    <AdminShell
      title="Blueprint phỏng vấn"
      subtitle="Quản lý mẫu cấu hình buổi phỏng vấn theo vị trí · cấp · loại."
      breadcrumb={[
        { label: 'Tổng quan', to: '/admin' },
        { label: 'Blueprint phỏng vấn' },
      ]}
      actions={
        <Button onClick={() => navigate('/admin/blueprints/new')}>
          <Plus className="h-4 w-4" />
          Tạo blueprint
        </Button>
      }
    >
      <Toast toast={toast} onClose={() => setToast(null)} />

      {/* Filters */}
      <div className="mb-6 grid grid-cols-1 gap-3 rounded-2xl border border-outline-variant bg-surface-container-lowest p-4 sm:grid-cols-2 lg:grid-cols-4">
        <EnumSelect<BlueprintRole>
          value={filters.targetRole as BlueprintRole | undefined}
          onChange={(v) => setFilters((f) => ({ ...f, targetRole: v || undefined }))}
          options={[...BLUEPRINT_ROLES]}
          labels={BLUEPRINT_ROLE_LABEL}
          placeholder="Mọi vị trí"
        />
        <EnumSelect<Level>
          value={filters.level as Level | undefined}
          onChange={(v) => setFilters((f) => ({ ...f, level: v || undefined }))}
          options={LEVELS}
          labels={LEVEL_LABEL}
          placeholder="Mọi cấp"
        />
        <EnumSelect<InterviewType>
          value={filters.interviewType}
          onChange={(v) => setFilters((f) => ({ ...f, interviewType: v || undefined }))}
          options={INTERVIEW_TYPES}
          labels={INTERVIEW_TYPE_LABEL}
          placeholder="Mọi loại"
        />
        <Select value={isDefaultValue} onChange={(e) => onIsDefaultChange(e.target.value)}>
          <option value="">Tất cả</option>
          <option value="true">Chỉ mặc định</option>
          <option value="false">Chưa mặc định</option>
        </Select>
        <div className="flex items-center gap-2 sm:col-span-2 lg:col-span-4">
          <Button onClick={applyFilters}>Áp dụng lọc</Button>
          <Button variant="ghost" onClick={clearFilters}>
            Xoá lọc
          </Button>
          {totalCount !== null && (
            <span className="ml-auto text-sm text-on-surface-variant">
              {totalCount} blueprint
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
        <EmptyState message="Chưa có blueprint nào khớp bộ lọc." />
      ) : (
        <div className="space-y-3">
          {items.map((b) => (
            <BlueprintCard
              key={b.id}
              b={b}
              settingDefault={settingDefault === b.id}
              onSetDefault={() => onSetDefault(b)}
              onEdit={() => goEdit(b)}
              onDelete={() => setPendingDelete(b)}
            />
          ))}
          {hasNext && (
            <div className="pt-2 text-center">
              <Button variant="outline" loading={loadingMore} onClick={onLoadMore}>
                Tải thêm
              </Button>
            </div>
          )}
        </div>
      )}

      <ConfirmDialog
        open={!!pendingDelete}
        title="Xoá blueprint?"
        message="Hành động này không thể hoàn tác. Nếu vẫn còn phiên phỏng vấn dùng blueprint này, hệ thống sẽ chặn xoá."
        confirmText="Xoá"
        danger
        loading={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </AdminShell>
  );
}

function roleLabel(role: string): string {
  return (BLUEPRINT_ROLE_LABEL as Record<string, string>)[role] ?? role;
}

function levelLabel(level: string): string {
  return (LEVEL_LABEL as Record<string, string>)[level] ?? level;
}

function BlueprintCard({
  b,
  settingDefault,
  onSetDefault,
  onEdit,
  onDelete,
}: {
  b: Blueprint;
  settingDefault: boolean;
  onSetDefault: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const isCoding = b.interviewType === 'CODING';
  const sortedTopics = [...b.topics].sort((x, y) => x.orderHint - y.orderHint);

  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4 transition hover:border-secondary/40">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold text-on-surface">
              {roleLabel(b.targetRole)} · {levelLabel(b.level)} ·{' '}
              {INTERVIEW_TYPE_LABEL[b.interviewType]}
            </p>
            {b.isDefault && (
              <Pill tone="secondary">
                <Star className="mr-1 h-3 w-3 fill-current" />
                Mặc định
              </Pill>
            )}
          </div>

          {/* Budget summary */}
          <p className="mt-1 text-xs text-on-surface-variant">
            {isCoding
              ? `${sortedTopics.length} slot · ${b.timeBudgetMinutes} phút`
              : `${sortedTopics.length} chủ đề · ${b.questionBudget} câu · ${b.timeBudgetMinutes} phút · follow-up ${b.maxFollowUpsPerTopic}/chủ đề · ${b.maxFollowUpsPerSession}/phiên · AI: ${b.useAiSelector ? 'bật' : 'tắt'}`}
          </p>

          {/* Topics / difficulty ramp */}
          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            {isCoding ? (
              sortedTopics.map((t, i) => (
                <DifficultyPill key={i} difficulty={t.targetDifficulty} />
              ))
            ) : (
              <>
                {sortedTopics.slice(0, 5).map((t, i) => (
                  <Pill key={i} tone="secondary">
                    {topicLabel(t.topicValue)} · {IMPORTANCE_LABEL[t.importance]} ·{' '}
                    {DIFFICULTY_LABEL[t.targetDifficulty]}
                  </Pill>
                ))}
                {sortedTopics.length > 5 && (
                  <Pill>+{sortedTopics.length - 5} chủ đề</Pill>
                )}
              </>
            )}
          </div>

          <p className="mt-2 text-xs text-on-surface-variant">
            Cập nhật {DATE_FMT.format(new Date(b.updatedAt))}
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
          {!b.isDefault && (
            <Button
              variant="outline"
              loading={settingDefault}
              onClick={onSetDefault}
              className="!py-1.5 text-xs"
            >
              <Star className="h-3.5 w-3.5" />
              Đặt mặc định
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

const TOPIC_LABELS: Record<string, string> = {
  ...(COMPETENCY_LABEL as Record<string, string>),
  ...(DOMAIN_LABEL as Record<string, string>),
};

/** Human label for a topic value (competency/domain enum); falls back to raw. */
function topicLabel(value: string | null): string {
  if (!value) return '—';
  return TOPIC_LABELS[value] ?? value;
}
