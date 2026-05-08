import { unwrap } from '@/api/client';
import type {
  AnswerView,
  PageResponse,
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
): Promise<PageResponse<SessionSummaryView>> {
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
