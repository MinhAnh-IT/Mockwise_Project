import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  BadgeCheck,
  Ban,
  ChevronLeft,
  ChevronRight,
  Search,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { listAdminProfiles, getProfileStats } from '@/api/adminProfile';
import { listAdminTracks, listAdminLevels } from '@/api/adminCatalog';
import { blockUser, unblockUser } from '@/api/adminUsers';
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
  Toast,
  type ToastState,
} from '@/components/admin/ui';
import type {
  AdminProfileFilters,
  AdminUserProfile,
  PositionLevel,
  PositionTrack,
} from '@/types/profile';

const PAGE_SIZE = 10;

const EMPTY_FILTERS: AdminProfileFilters = {};

const fmtInt = (n: number) => new Intl.NumberFormat('vi-VN').format(n);

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});
const DATETIME_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});
const fmtDate = (iso: string | null | undefined) =>
  iso ? DATE_FMT.format(new Date(iso)) : '—';
const fmtDateTime = (iso: string | null | undefined) =>
  iso ? DATETIME_FMT.format(new Date(iso)) : 'Chưa đăng nhập';

export default function AdminProfilesPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [toast, setToast] = useState<ToastState>(null);
  const flashShownRef = useRef(false);

  const [tracks, setTracks] = useState<PositionTrack[]>([]);
  const [levels, setLevels] = useState<PositionLevel[]>([]);

  // Draft filters bound to the controls; committed `filters` drive the fetch.
  const [draft, setDraft] = useState<AdminProfileFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<AdminProfileFilters>(EMPTY_FILTERS);

  const [items, setItems] = useState<AdminUserProfile[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState<number | null>(null);
  const [stat, setStat] = useState<number | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Block/unblock moderation state
  const [busyId, setBusyId] = useState<string | null>(null);
  const [pendingBlock, setPendingBlock] = useState<AdminUserProfile | null>(null);

  // Catalog (for the filter dropdowns) + total-profiles stat — loaded once.
  useEffect(() => {
    let cancelled = false;
    Promise.all([listAdminTracks(), listAdminLevels()])
      .then(([t, l]) => {
        if (cancelled) return;
        setTracks(t);
        setLevels(l);
      })
      .catch(() => {
        /* dropdowns degrade to empty — the table still works */
      });
    getProfileStats()
      .then((s) => {
        if (!cancelled) setStat(s.totalProfiles);
      })
      .catch(() => {
        if (!cancelled) setStat(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const load = useCallback(
    (p: number) => {
      let cancelled = false;
      setLoading(true);
      setError(null);
      listAdminProfiles(filters, p, PAGE_SIZE)
        .then((res) => {
          if (cancelled) return;
          setItems(res.items);
          setPage(res.page);
          setTotalPages(res.totalPages);
          setTotalElements(res.totalElements);
        })
        .catch((err) => {
          if (cancelled) return;
          if (err instanceof ApiError && err.status === 401) return;
          setError(
            err instanceof Error ? err.message : 'Không tải được danh sách hồ sơ.',
          );
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
      return () => {
        cancelled = true;
      };
    },
    [filters],
  );

  // Refetch from page 0 whenever the committed filters change.
  useEffect(() => load(0), [load]);

  // Surface a success message the edit page handed off, then strip it so a
  // refresh/back doesn't replay the toast.
  useEffect(() => {
    const flash = (location.state as { flash?: string } | null)?.flash;
    if (flash && !flashShownRef.current) {
      flashShownRef.current = true;
      setToast({ kind: 'success', text: flash });
      navigate(location.pathname, { replace: true });
    }
  }, [location.state, location.pathname, navigate]);

  const applyFilters = () => setFilters(draft);
  const clearFilters = () => {
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
  };

  const trackName = (id: string) =>
    tracks.find((t) => t.id === id)?.name ?? null;

  const setBlockedLocally = (userId: string, blocked: boolean) =>
    setItems((list) =>
      list.map((it) => (it.userId === userId ? { ...it, blocked } : it)),
    );

  const confirmBlock = async () => {
    if (!pendingBlock) return;
    const target = pendingBlock;
    setBusyId(target.userId);
    setPendingBlock(null);
    try {
      await blockUser(target.userId);
      setBlockedLocally(target.userId, true);
      setToast({ kind: 'success', text: `Đã chặn ${target.fullName}.` });
    } catch (err) {
      setToast({
        kind: 'error',
        text: err instanceof ApiError ? err.message : 'Chặn thất bại.',
      });
    } finally {
      setBusyId(null);
    }
  };

  const onUnblock = async (target: AdminUserProfile) => {
    setBusyId(target.userId);
    try {
      await unblockUser(target.userId);
      setBlockedLocally(target.userId, false);
      setToast({ kind: 'success', text: `Đã bỏ chặn ${target.fullName}.` });
    } catch (err) {
      setToast({
        kind: 'error',
        text: err instanceof ApiError ? err.message : 'Bỏ chặn thất bại.',
      });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <AdminShell
      title="Người dùng"
      subtitle="Xem hồ sơ ứng viên và chặn/bỏ chặn tài khoản."
      breadcrumb={[{ label: 'Tổng quan', to: '/admin' }, { label: 'Người dùng' }]}
    >
      <Toast toast={toast} onClose={() => setToast(null)} />

      {/* Stat band */}
      <div className="mb-6 flex items-center gap-4 rounded-2xl border border-outline-variant bg-gradient-to-br from-secondary-fixed via-surface-container-lowest to-surface-container-low p-5">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-secondary/10 text-secondary">
          <Users className="h-6 w-6" />
        </div>
        <div>
          <p className="text-2xl font-extrabold leading-none text-on-surface">
            {stat === null ? '—' : fmtInt(stat)}
          </p>
          <p className="mt-1 text-sm text-on-surface-variant">
            tổng số hồ sơ người dùng
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="mb-6 grid grid-cols-1 gap-3 rounded-2xl border border-outline-variant bg-surface-container-lowest p-4 sm:grid-cols-2 lg:grid-cols-4">
        <Select
          value={draft.trackId ?? ''}
          onChange={(e) =>
            setDraft((d) => ({ ...d, trackId: e.target.value || undefined }))
          }
        >
          <option value="">Mọi lĩnh vực</option>
          {tracks.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
              {t.active ? '' : ' (ẩn)'}
            </option>
          ))}
        </Select>
        <Select
          value={draft.levelId ?? ''}
          onChange={(e) =>
            setDraft((d) => ({ ...d, levelId: e.target.value || undefined }))
          }
        >
          <option value="">Mọi cấp độ</option>
          {levels.map((l) => (
            <option key={l.id} value={l.id}>
              {l.positionRole}
              {l.active ? '' : ' (ẩn)'}
            </option>
          ))}
        </Select>
        <div className="relative sm:col-span-2 lg:col-span-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
          <Input
            value={draft.keyword ?? ''}
            onChange={(e) =>
              setDraft((d) => ({ ...d, keyword: e.target.value || undefined }))
            }
            onKeyDown={(e) => {
              if (e.key === 'Enter') applyFilters();
            }}
            placeholder="Tên, email…"
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2 sm:col-span-2 lg:col-span-4">
          <Button onClick={applyFilters}>Áp dụng lọc</Button>
          <Button variant="ghost" onClick={clearFilters}>
            Xoá lọc
          </Button>
          {totalElements !== null && (
            <span className="ml-auto text-sm text-on-surface-variant">
              {fmtInt(totalElements)} hồ sơ
            </span>
          )}
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <Spinner label="Đang tải hồ sơ…" />
      ) : error ? (
        <ErrorState message={error} onRetry={() => load(page)} />
      ) : items.length === 0 ? (
        <EmptyState message="Không có hồ sơ nào khớp bộ lọc." />
      ) : (
        <div className="overflow-hidden rounded-2xl border border-outline-variant">
          <table className="w-full text-left text-sm">
            <thead className="bg-surface-container-low text-xs uppercase tracking-wide text-on-surface-variant">
              <tr>
                <th className="px-4 py-3 font-semibold">Người dùng</th>
                <th className="px-4 py-3 font-semibold">Vị trí</th>
                <th className="hidden px-4 py-3 font-semibold md:table-cell">
                  Ngày tạo
                </th>
                <th className="hidden px-4 py-3 font-semibold lg:table-cell">
                  Đăng nhập gần nhất
                </th>
                <th className="hidden px-4 py-3 text-right font-semibold sm:table-cell">
                  Kinh nghiệm
                </th>
                <th className="px-4 py-3 text-right font-semibold">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {items.map((p) => (
                <tr
                  key={p.userId}
                  className="bg-surface-container-lowest transition hover:bg-surface-container-low"
                >
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-on-surface">
                        {p.fullName}
                      </span>
                      {p.isVerified && (
                        <BadgeCheck
                          className="h-4 w-4 text-emerald-600"
                          aria-label="Đã xác thực"
                        />
                      )}
                      {p.blocked && (
                        <Pill tone="red">
                          <Ban className="mr-1 h-3 w-3" />
                          Bị chặn
                        </Pill>
                      )}
                    </div>
                    {p.email && (
                      <p className="text-xs text-on-surface-variant">{p.email}</p>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <Pill tone="secondary">
                        {p.position.trackName ??
                          trackName(p.position.trackId) ??
                          '—'}
                      </Pill>
                      <Pill>{p.position.levelName ?? '—'}</Pill>
                    </div>
                  </td>
                  <td className="hidden px-4 py-3 text-on-surface-variant md:table-cell">
                    {fmtDate(p.createdAt)}
                  </td>
                  <td className="hidden px-4 py-3 text-on-surface-variant lg:table-cell">
                    {fmtDateTime(p.lastLoginAt)}
                  </td>
                  <td className="hidden px-4 py-3 text-right text-on-surface-variant sm:table-cell">
                    {p.experience} năm
                  </td>
                  <td className="px-4 py-3 text-right">
                    {p.blocked ? (
                      <Button
                        variant="outline"
                        loading={busyId === p.userId}
                        onClick={() => onUnblock(p)}
                        className="!py-1.5 text-xs"
                      >
                        <ShieldCheck className="h-4 w-4" />
                        Bỏ chặn
                      </Button>
                    ) : (
                      <Button
                        variant="danger"
                        loading={busyId === p.userId}
                        onClick={() => setPendingBlock(p)}
                        className="!py-1.5 text-xs"
                      >
                        <Ban className="h-4 w-4" />
                        Chặn
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {!loading && !error && totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <Button
            variant="outline"
            disabled={page <= 0}
            onClick={() => load(page - 1)}
          >
            <ChevronLeft className="h-4 w-4" />
            Trước
          </Button>
          <span className="px-2 text-sm text-on-surface-variant">
            Trang {page + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            disabled={page + 1 >= totalPages}
            onClick={() => load(page + 1)}
          >
            Sau
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={!!pendingBlock}
        title="Chặn tài khoản này?"
        message={
          <>
            Chặn <strong>{pendingBlock?.fullName}</strong>
            {pendingBlock?.email ? ` (${pendingBlock.email})` : ''}? Người dùng sẽ
            bị đăng xuất khỏi mọi thiết bị và không thể đăng nhập lại cho tới khi
            được bỏ chặn.
          </>
        }
        confirmText="Chặn"
        danger
        loading={busyId === pendingBlock?.userId}
        onConfirm={confirmBlock}
        onCancel={() => setPendingBlock(null)}
      />
    </AdminShell>
  );
}
