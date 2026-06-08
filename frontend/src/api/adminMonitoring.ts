/**
 * Admin server-monitoring client. The endpoint is served by the api-gateway
 * itself (not proxied to a backend) at /api/admin/monitoring/** and is gated to
 * ROLE_ADMIN by the gateway's MonitoringAuthWebFilter. Wrapped in the standard
 * ApiResponse envelope → unwrap.
 */
import { unwrap } from '@/api/client';
import type { MonitoringOverview } from '@/types/monitoring';

export const getMonitoringOverview = (): Promise<MonitoringOverview> =>
  unwrap<MonitoringOverview>('/api/admin/monitoring/overview');
