export type ComponentState = 'UP' | 'DEGRADED' | 'DOWN';

export type ComponentStatus = {
  name: string;
  status: ComponentState;
  /** Round-trip latency in ms; null when DOWN. */
  latencyMs: number | null;
  detail: string | null;
};

export type HostMetrics = {
  /** 0–100, or null when the JVM can't read CPU load yet. */
  cpuLoadPercent: number | null;
  systemLoadAverage: number | null;
  memTotalBytes: number;
  memUsedBytes: number;
  diskTotalBytes: number;
  diskFreeBytes: number;
};

export type RuntimeInfo = {
  service: string;
  javaVersion: string;
  availableProcessors: number;
  uptimeMs: number;
  startedAt: string;
};

export type MonitoringOverview = {
  services: ComponentStatus[];
  infra: ComponentStatus[];
  host: HostMetrics;
  runtime: RuntimeInfo;
  generatedAt: string;
};
