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

/**
 * Single-step interview-video upload — replaces the 3-step presigned-PUT
 * dance (createVideoUpload → MinIO PUT → completeVideoUpload). The previous
 * flow died on Mixed Content blocking: the app loads over HTTPS but the
 * MinIO host that the presigned URL points at is HTTP-only, so the browser
 * refused the cross-origin PUT.
 *
 * Going through storage-service costs an extra hop (browser → our server →
 * MinIO instead of browser → MinIO direct), but for a 5–10 minute clip on
 * VPS bandwidth it's a flat ~few seconds and avoids any DNS / cert work.
 *
 * Returns the StorageObject with status = READY so the caller can pass
 * `objectId` straight to interview-service's submit-answer endpoint.
 */
export function uploadVideoMultipart(
  blob: Blob,
  sessionId: string,
  filename = 'answer.webm',
): Promise<StorageObject> {
  const form = new FormData();
  form.append('file', blob, filename);
  form.append('sessionId', sessionId);
  return unwrap(`${PREFIX}/uploads/videos/multipart`, {
    method: 'POST',
    body: form,
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
