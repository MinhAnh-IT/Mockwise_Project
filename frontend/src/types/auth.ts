export type UserRole = 'USER' | 'ADMIN';

export type AccountRequest = {
  email: string;
  password: string;
};

export type ProfileDraftRequest = {
  fullName: string;
  trackId: string;
  levelId: string;
  city: string;
  experience: number;
};

export type RegisterRequest = {
  account: AccountRequest;
  profile: ProfileDraftRequest;
};

export type LoginResponseData = {
  accessToken: string;
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
