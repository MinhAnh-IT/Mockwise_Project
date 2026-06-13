export type UserRole = 'USER' | 'ADMIN';

export type AccountRequest = {
  email: string;
  password: string;
};

import type { Language } from '@/types/profile';

export type ProfileDraftRequest = {
  fullName: string;
  trackId: string;
  levelId: string;
  city: string;
  experience: number;
  /**
   * Optional first-pass profile fields. Users can register with only the
   * required ones; richer signals (techStack / industries / yearsInCurrentRole)
   * can be filled later via /profile/edit.
   */
  preferredLanguage?: Language;
  techStack?: string[];
  yearsInCurrentRole?: number;
  industries?: string[];
};

export type RegisterRequest = {
  account: AccountRequest;
  profile: ProfileDraftRequest;
};

export type LoginResponseData = {
  accessToken: string;
};

export type OAuthProviderName = 'google' | 'github';

export type OAuthLoginResponseData = {
  accessToken: string;
  /** When false, the SPA must route the user to complete their profile first. */
  profileCompleted: boolean;
};

export type UserResponse = {
  userId: string;
  email: string;
  role: UserRole;
  isVerified: boolean;
};

export type UserProfileSnapshot = {
  userId: string;
  fullName: string;
  city: string;
  experience: number;
};

export type RegisterResponseData = {
  user: UserResponse;
  profile: UserProfileSnapshot;
};

export type VerifyAccountOtpRequest = {
  email: string;
  otp: string;
};

export type ResetPasswordRequest = {
  email: string;
  otp: string;
  newPassword: string;
};
