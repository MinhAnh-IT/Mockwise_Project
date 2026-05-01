import { unwrap } from '@/api/client';

const PREFIX = '/api/v1/storage';

export type AvatarUploadReservation = {
  objectId: string;
  bucket: string;
  objectKey: string;
  uploadUrl: string;
  expiresAt: string;
};

export type CreateAvatarUploadRequest = {
  contentType: string;
  sizeBytes: number;
};

export function reserveAvatarUpload(
  payload: CreateAvatarUploadRequest,
): Promise<AvatarUploadReservation> {
  return unwrap(`${PREFIX}/uploads/avatars`, {
    method: 'POST',
    body: payload,
  });
}

export function completeAvatarUpload(objectId: string): Promise<void> {
  return unwrap(`${PREFIX}/uploads/avatars/${objectId}/complete`, {
    method: 'POST',
  });
}

/**
 * PUTs the file bytes directly to MinIO using the presigned URL returned by
 * `reserveAvatarUpload`. This is a cross-origin request to MinIO, so we
 * deliberately don't include credentials and don't go through the API client
 * wrapper (no JWT, no envelope unwrap).
 */
export async function putAvatarBytes(
  uploadUrl: string,
  file: File,
): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    body: file,
    headers: { 'Content-Type': file.type },
  });
  if (!response.ok) {
    throw new Error(`Avatar upload failed: HTTP ${response.status}`);
  }
}
