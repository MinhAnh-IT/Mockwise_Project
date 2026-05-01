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
};

export type UserProfileUpdateRequest = {
  fullName?: string;
  trackId?: string;
  levelId?: string;
  city?: string;
  experience?: number;
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
