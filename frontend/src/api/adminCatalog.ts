/**
 * Admin catalog client (user-profile-service): position tracks & levels.
 *
 * Envelope notes:
 * - getAll → ApiResponse(ApiListResponse) ({ totalCount, items }) → `unwrap`.
 * - create/update/getById/toggle/enable/disable → ApiResponse(entity) → `unwrap`.
 * - delete → 204 No Content → fire-and-forget `request`.
 *
 * Unlike the public `/position-tracks` + `/position-levels` endpoints (active
 * only), the admin endpoints return both active and inactive rows.
 */
import { request, unwrap } from '@/api/client';
import type {
  PositionLevel,
  PositionLevelInput,
  PositionTrack,
  PositionTrackInput,
} from '@/types/profile';

type ListWrapped<T> = { totalCount: number | null; items: T[] };

const TRACKS = '/api/v1/admin/position-tracks';
const LEVELS = '/api/v1/admin/position-levels';

// ── Position tracks ──────────────────────────────────────────────────────────

export async function listAdminTracks(): Promise<PositionTrack[]> {
  const res = await unwrap<ListWrapped<PositionTrack>>(TRACKS);
  return res.items;
}

export const createTrack = (body: PositionTrackInput): Promise<PositionTrack> =>
  unwrap<PositionTrack>(TRACKS, { method: 'POST', body });

export const updateTrack = (
  id: string,
  body: PositionTrackInput,
): Promise<PositionTrack> =>
  unwrap<PositionTrack>(`${TRACKS}/${id}`, { method: 'PUT', body });

export const toggleTrack = (id: string): Promise<PositionTrack> =>
  unwrap<PositionTrack>(`${TRACKS}/${id}/toggle`, { method: 'PATCH' });

export const deleteTrack = (id: string): Promise<void> =>
  request<void>(`${TRACKS}/${id}`, { method: 'DELETE' });

// ── Position levels ──────────────────────────────────────────────────────────

export async function listAdminLevels(): Promise<PositionLevel[]> {
  const res = await unwrap<ListWrapped<PositionLevel>>(LEVELS);
  return res.items;
}

export const createLevel = (body: PositionLevelInput): Promise<PositionLevel> =>
  unwrap<PositionLevel>(LEVELS, { method: 'POST', body });

export const updateLevel = (
  id: string,
  body: PositionLevelInput,
): Promise<PositionLevel> =>
  unwrap<PositionLevel>(`${LEVELS}/${id}`, { method: 'PUT', body });

export const toggleLevel = (id: string): Promise<PositionLevel> =>
  unwrap<PositionLevel>(`${LEVELS}/${id}/toggle`, { method: 'PATCH' });

export const deleteLevel = (id: string): Promise<void> =>
  request<void>(`${LEVELS}/${id}`, { method: 'DELETE' });
