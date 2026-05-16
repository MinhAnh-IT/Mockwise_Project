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
 * Streaming (upload-while-recording) video upload.
 *
 * <p>The legacy {@link uploadVideoPresigned} only starts after the user
 * clicks "Nộp" — the whole clip transfers while they wait. This instead
 * PUTs ~5MiB+ parts to MinIO *during* the recording (reusing the exact
 * same presigned-PUT + nginx-/minio/-proxy path), then asks storage to
 * compose them server-side at stop-time. The post-submit wait collapses
 * from "transfer N MB" to "compose, bytes already in MinIO".
 *
 * <p>Best-effort contract: {@link StreamingVideoUpload.finish} rejects on
 * any part/compose failure (or an empty recording) so the caller can
 * transparently fall back to {@link uploadVideoPresigned} with the
 * in-memory blob — never a correctness regression, only a slower path.
 *
 * <p>S3/MinIO compose requires every source part except the last to be
 * ≥ 5MiB — enforced here by buffering chunks before flushing a part.
 */
export type StreamingVideoUpload = {
  /** Feed one recorder chunk. Buffered; flushes a part once ≥5MiB. Never throws. */
  push: (chunk: Blob) => void;
  /** Flush the tail, compose server-side, resolve the final objectId (rejects → caller falls back). */
  finish: () => Promise<string>;
};

const STREAM_PART_MIN_BYTES = 5 * 1024 * 1024;

export function createStreamingVideoUpload(
  sessionId: string,
  contentType: string,
): StreamingVideoUpload {
  const initP = unwrap<{ objectId: string }>(
    `${PREFIX}/uploads/videos/stream/init`,
    { method: 'POST', body: { sessionId, contentType } },
  );

  let buffer: Blob[] = [];
  let bufferedBytes = 0;
  let nextPart = 1;
  let failed = false;
  // Serialises part PUTs so they land in order (1, 2, 3 …).
  let chain: Promise<void> = initP.then(() => undefined);

  async function putPart(objectId: string, partNumber: number, body: Blob) {
    const presigned = await unwrap<{ url: string }>(
      `${PREFIX}/uploads/videos/stream/${objectId}/part-url`,
      { method: 'GET', query: { partNumber } },
    );
    // Bare fetch — the presigned signature covers method/path/host; our
    // auth headers must NOT be attached (same rule as uploadVideoPresigned).
    const resp = await fetch(presigned.url, { method: 'PUT', body });
    if (!resp.ok) {
      throw new Error(`part ${partNumber} PUT failed (HTTP ${resp.status})`);
    }
  }

  function enqueueFlush(final: boolean) {
    if (!final && bufferedBytes < STREAM_PART_MIN_BYTES) return;
    if (buffer.length === 0) return;
    const parts = buffer;
    const partNumber = nextPart;
    buffer = [];
    bufferedBytes = 0;
    nextPart += 1;
    chain = chain
      .then(async () => {
        if (failed) return;
        const { objectId } = await initP;
        await putPart(objectId, partNumber, new Blob(parts, { type: contentType }));
      })
      .catch((e) => {
        failed = true;
        console.warn('[streaming-upload] part failed — will fall back:', e);
      });
  }

  return {
    push(chunk: Blob) {
      if (failed || !chunk || chunk.size === 0) return;
      buffer.push(chunk);
      bufferedBytes += chunk.size;
      if (bufferedBytes >= STREAM_PART_MIN_BYTES) enqueueFlush(false);
    },
    async finish(): Promise<string> {
      enqueueFlush(true); // tail part — any size, it's the last source
      await chain;
      if (failed) throw new Error('streaming upload failed');
      const partCount = nextPart - 1;
      if (partCount < 1) throw new Error('streaming upload produced no parts');
      const { objectId } = await initP;
      await unwrap(`${PREFIX}/uploads/videos/stream/${objectId}/complete`, {
        method: 'POST',
        body: { partCount },
      });
      return objectId;
    },
  };
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
