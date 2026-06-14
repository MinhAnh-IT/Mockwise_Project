/**
 * Admin audit-log client. Served by iam-service under /api/v1/iam/admin/audit —
 * routed by the gateway and gated to ROLE_ADMIN (any path containing /admin/).
 * Responses use the standard ApiResponse envelope → unwrap.
 */
import { unwrap } from '@/api/client';
import type { AuditFilters, AuditLog, AuditLogPage } from '@/types/audit';

const BASE = '/api/v1/iam/admin/audit';

export const getAuditLogs = (filters: AuditFilters): Promise<AuditLogPage> =>
  unwrap<AuditLogPage>(BASE, {
    query: {
      actorId: filters.actorId || undefined,
      action: filters.action || undefined,
      category: filters.category || undefined,
      targetType: filters.targetType || undefined,
      targetId: filters.targetId || undefined,
      outcome: filters.outcome || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      page: filters.page,
      size: filters.size,
    },
  });

export const getAuditLog = (id: number): Promise<AuditLog> =>
  unwrap<AuditLog>(`${BASE}/${id}`);
