export type Position = {
  positionId: string;
  trackId: string;
  trackName: string;
  levelId: string;
  levelName: string;
};

/**
 * TTS / AI feedback language. Mirrors the backend `Language` enum
 * (services/user-profile-service/.../entity/Language.java). Defaults to VI.
 */
export type Language = 'VI' | 'EN';

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
  /**
   * Tools / languages / frameworks (lowercased, e.g. ["java", "spring"]).
   * Backend normalizes case + dedupes on save, so we display whatever it
   * returns and write back lowercase tokens.
   */
  techStack?: string[];
  preferredLanguage?: Language;
  /**
   * Years in the current track/level. May be smaller than `experience` when
   * the user recently switched roles. Used by interview-service to refine
   * difficulty calibration.
   */
  yearsInCurrentRole?: number | null;
  /** Industries (lowercased, e.g. ["fintech", "ecommerce"]). */
  industries?: string[];
};

export type UserProfileUpdateRequest = {
  fullName?: string;
  trackId?: string;
  levelId?: string;
  city?: string;
  experience?: number;
  avatarObjectKey?: string;
  techStack?: string[];
  preferredLanguage?: Language;
  yearsInCurrentRole?: number;
  industries?: string[];
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
