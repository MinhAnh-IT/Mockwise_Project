import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Activity,
  Clock,
  Cpu,
  Database,
  HardDrive,
  MemoryStick,
  RefreshCw,
  Server,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getMonitoringOverview } from '@/api/adminMonitoring';
import {
  AdminShell,
  Button,
  ErrorState,
  Pill,
  Spinner,
} from '@/components/admin/ui';
import type {
  ComponentState,
  ComponentStatus,
  MonitoringOverview,
} from '@/types/monitoring';

const REFRESH_MS = 10_000;

const STATE_TONE: Record<ComponentState, 'emerald' | 'amber' | 'red'> = {
  UP: 'emerald',
  DEGRADED: 'amber',
  DOWN: 'red',
};

const STATE_LABEL: Record<ComponentState, string> = {
  UP: 'Hoạt động',
  DEGRADED: 'Suy giảm',
  DOWN: 'Mất kết nối',
};

const TIME_FMT = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

function fmtBytes(n: number): string {
  if (!n || n < 0) return '—';
  const gb = n / 1024 ** 3;
  if (gb >= 1) return `${gb.toFixed(1)} GB`;
  return `${(n / 1024 ** 2).toFixed(0)} MB`;
}

function fmtUptime(ms: number): string {
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (d > 0) return `${d}d ${h}h ${m}m`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function pct(used: number, total: number): number {
  if (!total) return 0;
  return Math.min(100, Math.max(0, (used / total) * 100));
}

export default function AdminMonitoringPage() {
  const [data, setData] = useState<MonitoringOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [auto, setAuto] = useState(true);
  const firstLoad = useRef(true);

  const load = useCallback(async () => {
    if (firstLoad.current) setLoading(true);
    else setRefreshing(true);
    try {
      const res = await getMonitoringOverview();
      setData(res);
      setError(null);
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) {
        setError(err instanceof Error ? err.message : 'Không tải được dữ liệu giám sát.');
      }
    } finally {
      firstLoad.current = false;
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!auto) return;
    const t = window.setInterval(load, REFRESH_MS);
    return () => window.clearInterval(t);
  }, [auto, load]);

  return (
    <AdminShell
      title="Giám sát hệ thống"
      subtitle="Tình trạng service, hạ tầng và tài nguyên máy chủ."
      breadcrumb={[
        { label: 'Tổng quan', to: '/admin' },
        { label: 'Giám sát hệ thống' },
      ]}
      actions={
        <div className="flex items-center gap-2">
          <label className="flex items-center gap-1.5 text-xs text-on-surface-variant">
            <input
              type="checkbox"
              checked={auto}
              onChange={(e) => setAuto(e.target.checked)}
              className="h-3.5 w-3.5 accent-secondary"
            />
            Tự động (10s)
          </label>
          <Button variant="outline" loading={refreshing} onClick={load}>
            <RefreshCw className="h-4 w-4" />
            Làm mới
          </Button>
        </div>
      }
    >
      {loading ? (
        <Spinner label="Đang thu thập tình trạng hệ thống…" />
      ) : error && !data ? (
        <ErrorState message={error} onRetry={load} />
      ) : data ? (
        <div className="space-y-8">
          {error && (
            <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-800">
              Lần làm mới gần nhất lỗi: {error}. Đang hiển thị dữ liệu cũ.
            </p>
          )}

          {/* Host resources */}
          <section>
            <SectionTitle icon={Cpu} title="Tài nguyên máy chủ" />
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard
                icon={Cpu}
                label="CPU"
                value={data.host.cpuLoadPercent == null ? '—' : `${data.host.cpuLoadPercent}%`}
                sub={
                  data.host.systemLoadAverage == null
                    ? `${data.runtime.availableProcessors} nhân`
                    : `load ${data.host.systemLoadAverage} · ${data.runtime.availableProcessors} nhân`
                }
                bar={data.host.cpuLoadPercent ?? undefined}
              />
              <MetricCard
                icon={MemoryStick}
                label="RAM"
                value={`${fmtBytes(data.host.memUsedBytes)} / ${fmtBytes(data.host.memTotalBytes)}`}
                sub={`${pct(data.host.memUsedBytes, data.host.memTotalBytes).toFixed(0)}% đã dùng`}
                bar={pct(data.host.memUsedBytes, data.host.memTotalBytes)}
              />
              <MetricCard
                icon={HardDrive}
                label="Ổ đĩa"
                value={`${fmtBytes(data.host.diskTotalBytes - data.host.diskFreeBytes)} / ${fmtBytes(data.host.diskTotalBytes)}`}
                sub={`còn trống ${fmtBytes(data.host.diskFreeBytes)}`}
                bar={pct(
                  data.host.diskTotalBytes - data.host.diskFreeBytes,
                  data.host.diskTotalBytes,
                )}
              />
              <MetricCard
                icon={Clock}
                label="Gateway uptime"
                value={fmtUptime(data.runtime.uptimeMs)}
                sub={`Java ${data.runtime.javaVersion}`}
              />
            </div>
          </section>

          {/* Services */}
          <section>
            <SectionTitle icon={Server} title="Microservices" count={data.services.length} />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {data.services.map((s) => (
                <StatusCard key={s.name} c={s} />
              ))}
            </div>
          </section>

          {/* Infra */}
          <section>
            <SectionTitle icon={Database} title="Hạ tầng" count={data.infra.length} />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {data.infra.map((s) => (
                <StatusCard key={s.name} c={s} />
              ))}
            </div>
          </section>

          <p className="flex items-center gap-1.5 text-xs text-on-surface-variant">
            <Activity className="h-3.5 w-3.5" />
            Cập nhật lúc {TIME_FMT.format(new Date(data.generatedAt))}
            {refreshing && ' · đang làm mới…'}
          </p>
        </div>
      ) : null}
    </AdminShell>
  );
}

function SectionTitle({
  icon: Icon,
  title,
  count,
}: {
  icon: typeof Server;
  title: string;
  count?: number;
}) {
  return (
    <h2 className="mb-3 flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-on-surface-variant">
      <Icon className="h-4 w-4" />
      {title}
      {count != null && <span className="font-normal opacity-70">({count})</span>}
    </h2>
  );
}

function MetricCard({
  icon: Icon,
  label,
  value,
  sub,
  bar,
}: {
  icon: typeof Server;
  label: string;
  value: string;
  sub?: string;
  bar?: number;
}) {
  const barTone =
    bar == null
      ? ''
      : bar >= 90
        ? 'bg-red-500'
        : bar >= 75
          ? 'bg-amber-500'
          : 'bg-emerald-500';
  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <p className="mt-2 text-lg font-extrabold leading-tight text-on-surface">{value}</p>
      {sub && <p className="text-xs text-on-surface-variant">{sub}</p>}
      {bar != null && (
        <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface-container">
          <div className={`h-full rounded-full ${barTone}`} style={{ width: `${bar}%` }} />
        </div>
      )}
    </div>
  );
}

function StatusCard({ c }: { c: ComponentStatus }) {
  const dotTone =
    c.status === 'UP' ? 'bg-emerald-500' : c.status === 'DEGRADED' ? 'bg-amber-500' : 'bg-red-500';
  return (
    <div className="flex items-center justify-between gap-3 rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${dotTone}`} />
          <span className="truncate font-semibold text-on-surface">{c.name}</span>
        </div>
        {c.detail && (
          <p className="mt-0.5 truncate text-xs text-on-surface-variant" title={c.detail}>
            {c.detail}
          </p>
        )}
      </div>
      <div className="shrink-0 text-right">
        <Pill tone={STATE_TONE[c.status]}>{STATE_LABEL[c.status]}</Pill>
        {c.latencyMs != null && (
          <p className="mt-1 text-xs text-on-surface-variant">{c.latencyMs} ms</p>
        )}
      </div>
    </div>
  );
}
