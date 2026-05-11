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
 * 3-step presigned upload for interview-video answers:
 *   1. POST /uploads/videos to reserve an object + obtain a presigned PUT URL
 *   2. PUT the bytes straight to MinIO at that URL
 *   3. POST /uploads/videos/{objectId}/complete so storage-service flips
 *      the row to READY (stats MinIO, validates declared size)
 *
 * Going browser → MinIO direct skips the streaming-through-server hop the
 * old multipart endpoint had, so a 10-minute 720p clip lands seconds faster
 * and storage-service doesn't sit on the bytes. Mixed-content blocking is
 * neutralised because the presigned URL points at the same-origin nginx
 * /minio/ proxy (HTTPS) which forwards to MinIO upstream.
 *
 * Returns the StorageObject with status = READY so the caller can pass
 * `objectId` straight to interview-service's submit-answer endpoint.
 */
export type VideoUploadTicket = {
  objectId: string;
  bucket: string;
  objectKey: string;
  uploadUrl: string;
  expiresAt: string;
};

export async function uploadVideoPresigned(
  blob: Blob,
  sessionId: string,
  contentType = 'video/webm',
): Promise<StorageObject> {
  const ticket = await unwrap<VideoUploadTicket>(`${PREFIX}/uploads/videos`, {
    method: 'POST',
    body: {
      sessionId,
      contentType,
      sizeBytes: blob.size,
    },
  });

  // PUT bytes straight to MinIO. The presigned URL signature covers method,
  // path, host, and the X-Amz-* query params; we must NOT send extra signed
  // headers (e.g. an explicit Content-Type) or the signature breaks.
  // Using bare fetch — `requestRaw`/`unwrap` would attach our auth headers.
  const putResp = await fetch(ticket.uploadUrl, {
    method: 'PUT',
    body: blob,
  });
  if (!putResp.ok) {
    throw new Error(
      `Tải video lên MinIO thất bại (HTTP ${putResp.status})`,
    );
  }

  return unwrap<StorageObject>(
    `${PREFIX}/uploads/videos/${ticket.objectId}/complete`,
    { method: 'POST' },
  );
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
