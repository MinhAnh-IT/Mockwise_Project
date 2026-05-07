import { requestRaw, unwrap } from '@/api/client';

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

export type CreateVideoUploadRequest = {
  sessionId: string;
  contentType: string;
  sizeBytes: number;
};

export type VideoUploadTicket = {
  objectId: string;
  bucket: string;
  objectKey: string;
  uploadUrl: string;
  expiresAt: string;
};

/**
 * Step 1 of the 3-step video upload: ask storage-service for a presigned PUT
 * URL pointing at MinIO. Browser uploads bytes to that URL directly (no
 * proxy through our backend) so a 50 MB clip doesn't touch our process.
 */
export function createVideoUpload(
  req: CreateVideoUploadRequest,
): Promise<VideoUploadTicket> {
  return unwrap(`${PREFIX}/uploads/videos`, {
    method: 'POST',
    body: req,
  });
}

/**
 * Step 2: PUT the video bytes to the presigned URL. Uses native fetch (not the
 * authed client) since the URL is already signed and a Bearer header would be
 * rejected by MinIO. Throws if MinIO returns a non-2xx — caller decides how
 * to surface.
 */
export async function putVideoBytes(
  uploadUrl: string,
  blob: Blob,
  contentType: string,
): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    body: blob,
    headers: { 'Content-Type': contentType },
  });
  if (!response.ok) {
    throw new Error(`Video upload to MinIO failed: ${response.status}`);
  }
}

/**
 * Step 3: tell storage-service the upload is done. Storage flips status from
 * PENDING_UPLOAD to READY after verifying object exists in MinIO. The object
 * is only safe to reference in /answers after this returns.
 */
export function completeVideoUpload(objectId: string): Promise<StorageObject> {
  return unwrap(`${PREFIX}/uploads/videos/${objectId}/complete`, {
    method: 'POST',
  });
}

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

/**
 * Fetch a JWT-protected binary endpoint (e.g. the avatar bytes) and return a
 * blob URL the caller can drop into an `<img src>`. Caller is responsible for
 * `URL.revokeObjectURL` when the URL is no longer needed.
 */
export async function fetchAuthedBlobUrl(path: string): Promise<string> {
  const response = await requestRaw(path);
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}
