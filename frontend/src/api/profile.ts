import { unwrap } from '@/api/client';
import type { UserProfile, UserProfileUpdateRequest } from '@/types/profile';

const PREFIX = '/api/v1/profiles';

export function getMyProfile(): Promise<UserProfile> {
  return unwrap(PREFIX);
}

export function getProfileByUserId(userId: string): Promise<UserProfile> {
  return unwrap(`${PREFIX}/${userId}`);
}

export function updateMyProfile(
  userId: string,
  payload: UserProfileUpdateRequest,
): Promise<UserProfile> {
  return unwrap(`${PREFIX}/${userId}`, {
    method: 'PATCH',
    body: payload,
  });
}
