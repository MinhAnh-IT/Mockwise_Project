import { unwrap } from '@/api/client';
import type {
  AnswerView,
  ApiListResponse,
  SessionSummaryView,
  SessionView,
  StartSessionInput,
  StartSessionOutput,
  SubmitAnswerInput,
  SubmitAnswerOutput,
} from '@/types/interview';

const PREFIX = '/api/v1/interviews';

export function startSession(input: StartSessionInput): Promise<StartSessionOutput> {
  return unwrap(`${PREFIX}/start`, {
    method: 'POST',
    body: input,
  });
}

export function listSessions(
  page = 0,
  size = 20,
): Promise<ApiListResponse<SessionSummaryView>> {
  return unwrap(`${PREFIX}/result`, {
    query: { page, size },
  });
}

export function getSession(sessionId: string): Promise<SessionView> {
  return unwrap(`${PREFIX}/${sessionId}`);
}

export function finishSession(sessionId: string): Promise<void> {
  return unwrap(`${PREFIX}/${sessionId}/finish`, { method: 'POST' });
}

export function submitAnswer(
  sessionId: string,
  sessionQuestionId: string,
  input: SubmitAnswerInput,
): Promise<SubmitAnswerOutput> {
  return unwrap(`${PREFIX}/${sessionId}/questions/${sessionQuestionId}/answers`, {
    method: 'POST',
    body: input,
  });
}

export function getAnswer(sessionId: string, answerId: string): Promise<AnswerView> {
  return unwrap(`${PREFIX}/${sessionId}/answers/${answerId}`);
}

/**
 * Lever 2 (realtime-stt-plan.md §5 Provider C): mint a single-use ElevenLabs
 * realtime Scribe token. POST — each token is consumed on use. Throws (409
 * REALTIME_STT_DISABLED) when the backend kill-switch is off, which the caller
 * treats as "no fast path → legacy flow".
 */
export function getRealtimeSttToken(): Promise<{
  token: string;
  model: string;
  expiresInSeconds?: number;
}> {
  return unwrap(`${PREFIX}/realtime-stt/token`, { method: 'POST' });
}

/**
 * Lever 2 (realtime-stt-plan.md §6.2.3): attach the background-uploaded video to
 * a fast-path answer once its upload composes. Idempotent + safe to call after
 * the session has ended, so a late upload is never dropped. Returns 204.
 */
export function attachVideo(
  sessionId: string,
  answerId: string,
  storageObjectId: string,
): Promise<void> {
  return unwrap(`${PREFIX}/${sessionId}/answers/${answerId}/attach-video`, {
    method: 'POST',
    body: { storageObjectId },
  });
}
