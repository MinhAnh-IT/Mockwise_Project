/**
 * Interview blueprint admin client (interview-service).
 *
 * Envelope notes (differ from question-bank):
 * - LIST is wrapped in the ApiResponse envelope → use `unwrap`, which yields an
 *   `ApiListResponse` ({ totalCount, items }). (Question-bank list is bare.)
 * - get/create/update/setDefault return the ApiResponse envelope → `unwrap`.
 * - delete returns 204 / ApiResponse(null) → fire-and-forget `request`.
 */
import { request, unwrap } from '@/api/client';
import type { Blueprint, BlueprintFilters, BlueprintRequest } from '@/types/blueprint';

const BASE = '/api/v1/interviews/admin/blueprints';

export type ListPage<T> = { totalCount: number | null; items: T[] };

export function listBlueprints(
  filters: BlueprintFilters,
  page: number,
  size: number,
): Promise<ListPage<Blueprint>> {
  return unwrap<ListPage<Blueprint>>(BASE, {
    query: {
      targetRole: filters.targetRole,
      level: filters.level,
      interviewType: filters.interviewType,
      isDefault: filters.isDefault === undefined ? undefined : String(filters.isDefault),
      page,
      size,
    },
  });
}

export const getBlueprint = (id: string): Promise<Blueprint> =>
  unwrap<Blueprint>(`${BASE}/${id}`);

export const createBlueprint = (body: BlueprintRequest): Promise<Blueprint> =>
  unwrap<Blueprint>(BASE, { method: 'POST', body });

export const updateBlueprint = (id: string, body: BlueprintRequest): Promise<Blueprint> =>
  unwrap<Blueprint>(`${BASE}/${id}`, { method: 'PUT', body });

export const setBlueprintDefault = (id: string, isDefault: boolean): Promise<Blueprint> =>
  unwrap<Blueprint>(`${BASE}/${id}/default`, { method: 'PATCH', body: { isDefault } });

export const deleteBlueprint = (id: string): Promise<void> =>
  request<void>(`${BASE}/${id}`, { method: 'DELETE' });
