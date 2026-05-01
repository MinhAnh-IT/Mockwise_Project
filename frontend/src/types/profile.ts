export type Position = {
  positionId: string;
  trackId: string;
  trackName: string;
  levelId: string;
  levelName: string;
};

export type UserProfile = {
  userId: string;
  fullName: string;
  position: Position;
  city: string;
  experience: number;
  /**
   * Same-origin path served by storage-service (`/api/v1/storage/avatars/me`).
   * Includes a `?v=<key>` cache-buster so a re-upload bypasses the browser
   * cache. Null/undefined when the user has not uploaded an avatar.
   */
  avatarUrl?: string | null;
};

export type UserProfileUpdateRequest = {
  fullName?: string;
  trackId?: string;
  levelId?: string;
  city?: string;
  experience?: number;
  avatarObjectKey?: string;
};

export type PositionTrack = {
  id: string;
  name: string;
  active: boolean;
};

export type PositionLevel = {
  id: string;
  positionRole: string;
  active: boolean;
};
