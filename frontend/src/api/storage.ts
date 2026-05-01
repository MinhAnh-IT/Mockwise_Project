import { unwrap } from '@/api/client';

const PREFIX = '/api/v1/storage';

export type StorageKind = 'INTERVIEW_VIDEO' | 'QUESTION_AUDIO' | 'USER_AVATAR';
export type StorageStatus = 'PENDING_UPLOAD' | 'READY' | 'FAILED';

export type StorageObject = {
  objectId: string;
  kind: StorageKind;
  bucket: string;
  objectKey: string;
  contentType: string;
  sizeBytes: number;
  status: StorageStatus;
};

/**
 * Multipart avatar upload — file is streamed through storage-service to MinIO.
 * Going through the server (rather than browser → presigned PUT to MinIO
 * directly) avoids two cross-origin problems:
 *   1. mixed-content blocking (HTTPS app → HTTP MinIO host)
 *   2. CORS preflight on the MinIO bucket
 *
 * Avatars are capped at 5 MB so the extra hop is cheap.
 */
export function uploadAvatar(file: File): Promise<StorageObject> {
  const form = new FormData();
  form.append('file', file);

  return unwrap(`${PREFIX}/uploads/avatars`, {
    method: 'POST',
    body: form,
  });
}
