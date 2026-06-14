/** Types for the admin audit-log screen (served by iam-service at /admin/audit). */

export type AuditLog = {
  id: number;
  occurredAt: string;
  actorId: string | null;
  actorEmail: string | null;
  actorRole: string | null;
  action: string;
  category: string;
  targetType: string | null;
  targetId: string | null;
  httpMethod: string | null;
  path: string | null;
  statusCode: number | null;
  outcome: string;
  ip: string | null;
  userAgent: string | null;
  latencyMs: number | null;
  source: string;
  detail: string | null;
};

export type AuditLogPage = {
  content: AuditLog[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AuditFilters = {
  actorId?: string;
  action?: string;
  category?: string;
  targetType?: string;
  targetId?: string;
  outcome?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
};
