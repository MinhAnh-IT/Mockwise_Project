import { unwrap } from '@/api/client';
import type { PositionLevel, PositionTrack } from '@/types/profile';

type ListWrapped<T> = { items: T[] };

export async function listPositionTracks(): Promise<PositionTrack[]> {
  const result = await unwrap<ListWrapped<PositionTrack>>('/api/v1/position-tracks', {
    auth: false,
  });
  return result.items;
}

export async function listPositionLevels(): Promise<PositionLevel[]> {
  const result = await unwrap<ListWrapped<PositionLevel>>('/api/v1/position-levels', {
    auth: false,
  });
  return result.items;
}
