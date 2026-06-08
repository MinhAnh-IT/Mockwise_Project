import { useCallback, useEffect, useState } from 'react';
import { Check, Eye, EyeOff, Pencil, Plus, Trash2, X } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  createLevel,
  createTrack,
  deleteLevel,
  deleteTrack,
  listAdminLevels,
  listAdminTracks,
  toggleLevel,
  toggleTrack,
  updateLevel,
  updateTrack,
} from '@/api/adminCatalog';
import {
  AdminShell,
  Button,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Input,
  Pill,
  Spinner,
  Toast,
  type ToastState,
} from '@/components/admin/ui';

/** Common shape both tracks (name) and levels (positionRole) map onto. */
type CatalogItem = { id: string; label: string; active: boolean };

type CatalogAdapter = {
  key: string;
  title: string;
  subtitle: string;
  placeholder: string;
  maxLen: number;
  load: () => Promise<CatalogItem[]>;
  create: (label: string) => Promise<unknown>;
  rename: (id: string, label: string, active: boolean) => Promise<unknown>;
  toggle: (id: string) => Promise<unknown>;
  remove: (id: string) => Promise<unknown>;
};

const TRACK_ADAPTER: CatalogAdapter = {
  key: 'tracks',
  title: 'Lĩnh vực (Tracks)',
  subtitle: 'Mảng công việc: Backend, Frontend, DevOps…',
  placeholder: 'vd: Backend Developer',
  maxLen: 100,
  load: () =>
    listAdminTracks().then((rows) =>
      rows.map((t) => ({ id: t.id, label: t.name, active: t.active })),
    ),
  create: (label) => createTrack({ name: label }),
  rename: (id, label, active) => updateTrack(id, { name: label, active }),
  toggle: (id) => toggleTrack(id),
  remove: (id) => deleteTrack(id),
};

const LEVEL_ADAPTER: CatalogAdapter = {
  key: 'levels',
  title: 'Cấp độ (Levels)',
  subtitle: 'Cấp bậc vị trí: JUNIOR, MID, SENIOR, LEAD…',
  placeholder: 'vd: SENIOR',
  maxLen: 64,
  load: () =>
    listAdminLevels().then((rows) =>
      rows.map((l) => ({ id: l.id, label: l.positionRole, active: l.active })),
    ),
  create: (label) => createLevel({ positionRole: label }),
  rename: (id, label, active) => updateLevel(id, { positionRole: label, active }),
  toggle: (id) => toggleLevel(id),
  remove: (id) => deleteLevel(id),
};

export default function AdminCatalogPage() {
  const [toast, setToast] = useState<ToastState>(null);

  return (
    <AdminShell
      title="Vị trí & Cấp độ"
      subtitle="Quản lý danh mục lĩnh vực và cấp độ dùng để ghép thành vị trí (Position)."
      breadcrumb={[
        { label: 'Tổng quan', to: '/admin' },
        { label: 'Vị trí & Cấp độ' },
      ]}
    >
      <Toast toast={toast} onClose={() => setToast(null)} />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <CatalogSection adapter={TRACK_ADAPTER} onToast={setToast} />
        <CatalogSection adapter={LEVEL_ADAPTER} onToast={setToast} />
      </div>
    </AdminShell>
  );
}

function CatalogSection({
  adapter,
  onToast,
}: {
  adapter: CatalogAdapter;
  onToast: (t: ToastState) => void;
}) {
  const [items, setItems] = useState<CatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  // Inline create
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);

  // Inline edit
  const [editId, setEditId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');

  // Per-row busy (toggle) + delete confirm
  const [busyId, setBusyId] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<CatalogItem | null>(null);
  const [deleting, setDeleting] = useState(false);

  const refetch = useCallback(() => setReloadKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    adapter
      .load()
      .then((rows) => {
        if (!cancelled) setItems(rows);
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) return;
        setError(err instanceof Error ? err.message : 'Không tải được danh mục.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [adapter, reloadKey]);

  const fail = (err: unknown, fallback: string) =>
    onToast({
      kind: 'error',
      text: err instanceof ApiError ? err.message : fallback,
    });

  const submitCreate = async () => {
    const label = draft.trim();
    if (!label) return;
    setSaving(true);
    try {
      await adapter.create(label);
      onToast({ kind: 'success', text: 'Đã thêm mới.' });
      setDraft('');
      setCreating(false);
      refetch();
    } catch (err) {
      fail(err, 'Thêm thất bại.');
    } finally {
      setSaving(false);
    }
  };

  const submitEdit = async (item: CatalogItem) => {
    const label = editValue.trim();
    if (!label || label === item.label) {
      setEditId(null);
      return;
    }
    setBusyId(item.id);
    try {
      await adapter.rename(item.id, label, item.active);
      onToast({ kind: 'success', text: 'Đã cập nhật.' });
      setEditId(null);
      refetch();
    } catch (err) {
      fail(err, 'Cập nhật thất bại.');
    } finally {
      setBusyId(null);
    }
  };

  const onToggle = async (item: CatalogItem) => {
    setBusyId(item.id);
    try {
      await adapter.toggle(item.id);
      // Reflect locally without a round-trip; order is preserved.
      setItems((list) =>
        list.map((it) =>
          it.id === item.id ? { ...it, active: !it.active } : it,
        ),
      );
    } catch (err) {
      fail(err, 'Đổi trạng thái thất bại.');
    } finally {
      setBusyId(null);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await adapter.remove(pendingDelete.id);
      setItems((list) => list.filter((it) => it.id !== pendingDelete.id));
      onToast({ kind: 'success', text: 'Đã xoá.' });
      setPendingDelete(null);
    } catch (err) {
      // In-use rows (5012/5022) are blocked by the backend — keep the row.
      fail(err, 'Không xoá được. Có thể đang được sử dụng bởi một vị trí.');
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  };

  const startEdit = (item: CatalogItem) => {
    setEditId(item.id);
    setEditValue(item.label);
  };

  return (
    <section className="flex flex-col rounded-2xl border border-outline-variant bg-surface-container-lowest p-5">
      <div className="mb-1 flex items-start justify-between gap-2">
        <div>
          <h2 className="text-base font-bold text-on-surface">{adapter.title}</h2>
          <p className="text-xs text-on-surface-variant">{adapter.subtitle}</p>
        </div>
        {!creating && (
          <Button
            variant="outline"
            className="!py-1.5 text-xs"
            onClick={() => {
              setCreating(true);
              setDraft('');
            }}
          >
            <Plus className="h-4 w-4" />
            Thêm
          </Button>
        )}
      </div>

      {/* Inline create row */}
      {creating && (
        <div className="mt-3 flex items-center gap-2 rounded-xl border border-secondary/40 bg-surface-container-low p-2">
          <Input
            autoFocus
            value={draft}
            maxLength={adapter.maxLen}
            placeholder={adapter.placeholder}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submitCreate();
              if (e.key === 'Escape') setCreating(false);
            }}
          />
          <Button loading={saving} onClick={submitCreate} className="!px-3">
            <Check className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            onClick={() => setCreating(false)}
            className="!px-3"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* List */}
      <div className="mt-4 min-h-[6rem]">
        {loading ? (
          <Spinner />
        ) : error ? (
          <ErrorState message={error} onRetry={refetch} />
        ) : items.length === 0 ? (
          <EmptyState message="Chưa có mục nào." />
        ) : (
          <ul className="divide-y divide-outline-variant">
            {items.map((item) => {
              const isEditing = editId === item.id;
              const busy = busyId === item.id;
              return (
                <li
                  key={item.id}
                  className="flex items-center gap-2 py-2.5"
                >
                  {isEditing ? (
                    <>
                      <Input
                        autoFocus
                        value={editValue}
                        maxLength={adapter.maxLen}
                        onChange={(e) => setEditValue(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') submitEdit(item);
                          if (e.key === 'Escape') setEditId(null);
                        }}
                      />
                      <Button
                        loading={busy}
                        onClick={() => submitEdit(item)}
                        className="!px-3"
                      >
                        <Check className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        onClick={() => setEditId(null)}
                        className="!px-3"
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    </>
                  ) : (
                    <>
                      <span className="flex-1 truncate font-medium text-on-surface">
                        {item.label}
                      </span>
                      <Pill tone={item.active ? 'emerald' : 'amber'}>
                        {item.active ? 'Đang dùng' : 'Tạm ẩn'}
                      </Pill>
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => onToggle(item)}
                        className="rounded-lg p-2 text-on-surface-variant transition hover:bg-surface-container hover:text-secondary disabled:opacity-50"
                        aria-label={item.active ? 'Tạm ẩn' : 'Kích hoạt'}
                        title={item.active ? 'Tạm ẩn' : 'Kích hoạt'}
                      >
                        {item.active ? (
                          <EyeOff className="h-4 w-4" />
                        ) : (
                          <Eye className="h-4 w-4" />
                        )}
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => startEdit(item)}
                        className="rounded-lg p-2 text-on-surface-variant transition hover:bg-surface-container hover:text-secondary disabled:opacity-50"
                        aria-label="Sửa"
                        title="Sửa"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => setPendingDelete(item)}
                        className="rounded-lg p-2 text-on-surface-variant transition hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                        aria-label="Xoá"
                        title="Xoá"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Xoá mục này?"
        message={
          <>
            Xoá <strong>{pendingDelete?.label}</strong>? Nếu đang được dùng bởi một
            vị trí, hệ thống sẽ chặn xoá — hãy <em>tạm ẩn</em> thay thế.
          </>
        }
        confirmText="Xoá"
        danger
        loading={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </section>
  );
}
