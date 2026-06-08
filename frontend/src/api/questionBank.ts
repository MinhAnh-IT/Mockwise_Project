/**
 * Question-bank admin client.
 *
 * Envelope notes:
 * - Admin LIST endpoints return a bare `ApiListResponse` ({ totalCount, items })
 *   — NOT wrapped in the ApiResponse envelope — so they use `request`, not
 *   `unwrap`.
 * - Single-item create/update/get return the ApiResponse envelope → `unwrap`.
 * - status/delete return ApiResponse(null) / 204 → fire-and-forget `request`.
 * - AI `/generate-testcases` is FastAPI (no envelope, FastAPI `{detail}` on
 *   error) and sits behind the gateway's public `/api/ai/**` route, so it is
 *   called with a hand-rolled fetch that surfaces the `detail` message.
 */
import { request, unwrap } from '@/api/client';
import { API_BASE_URL } from '@/lib/env';
import type {
  AiGenerateRequest,
  AiGeneratedCoding,
  AnyQuestion,
  BehavioralFilters,
  BehavioralQuestion,
  BehavioralQuestionRequest,
  CodingFilters,
  CodingQuestion,
  CodingQuestionRequest,
  CoreFilters,
  CoreQuestion,
  CoreQuestionRequest,
  Difficulty,
  QuestionStatus,
} from '@/types/questionBank';

const ADMIN = '/api/v1/question-bank/admin/questions';
const PUBLIC = '/api/v1/question-bank/questions';

/** Bare list envelope used by the admin list endpoints. */
export type ListPage<T> = { totalCount: number | null; items: T[] };

type ListQuery = Record<string, string | number | undefined>;

function tagsParam(tags?: string[]): string | undefined {
  const clean = (tags ?? []).map((t) => t.trim()).filter(Boolean);
  return clean.length ? clean.join(',') : undefined;
}

// ── List ───────────────────────────────────────────────────────────────────

export function listBehavioral(
  filters: BehavioralFilters,
  page: number,
  size: number,
): Promise<ListPage<BehavioralQuestion>> {
  const query: ListQuery = {
    competency: filters.competency,
    difficulty: filters.difficulty,
    status: filters.status,
    tags: tagsParam(filters.tags),
    page,
    size,
  };
  return request(`${ADMIN}/behavioral`, { query });
}

export function listCore(
  filters: CoreFilters,
  page: number,
  size: number,
): Promise<ListPage<CoreQuestion>> {
  const query: ListQuery = {
    domain: filters.domain,
    targetRole: filters.targetRole,
    difficulty: filters.difficulty,
    status: filters.status,
    tags: tagsParam(filters.tags),
    page,
    size,
  };
  return request(`${ADMIN}/core`, { query });
}

export function listCoding(
  filters: CodingFilters,
  page: number,
  size: number,
): Promise<ListPage<CodingQuestion>> {
  const query: ListQuery = {
    difficulty: filters.difficulty,
    status: filters.status,
    tags: tagsParam(filters.tags),
    page,
    size,
  };
  return request(`${ADMIN}/coding`, { query });
}

// ── Read single (public route — used for edit deep-link refetch) ───────────

export function getQuestion(id: string): Promise<AnyQuestion> {
  return unwrap(`${PUBLIC}/${id}`);
}

// ── Create ─────────────────────────────────────────────────────────────────

export function createBehavioral(
  body: BehavioralQuestionRequest,
): Promise<BehavioralQuestion> {
  return unwrap(`${ADMIN}/behavioral`, { method: 'POST', body });
}

export function createCore(body: CoreQuestionRequest): Promise<CoreQuestion> {
  return unwrap(`${ADMIN}/core`, { method: 'POST', body });
}

export function createCoding(
  body: CodingQuestionRequest,
): Promise<CodingQuestion> {
  return unwrap(`${ADMIN}/coding`, { method: 'POST', body });
}

// ── Update ─────────────────────────────────────────────────────────────────

export function updateBehavioral(
  id: string,
  body: BehavioralQuestionRequest,
): Promise<BehavioralQuestion> {
  return unwrap(`${ADMIN}/behavioral/${id}`, { method: 'PUT', body });
}

export function updateCore(
  id: string,
  body: CoreQuestionRequest,
): Promise<CoreQuestion> {
  return unwrap(`${ADMIN}/core/${id}`, { method: 'PUT', body });
}

export function updateCoding(
  id: string,
  body: CodingQuestionRequest,
): Promise<CodingQuestion> {
  return unwrap(`${ADMIN}/coding/${id}`, { method: 'PUT', body });
}

// ── Status & delete ────────────────────────────────────────────────────────

export function updateStatus(
  id: string,
  status: QuestionStatus,
): Promise<void> {
  return request(`${ADMIN}/${id}/status`, {
    method: 'PATCH',
    body: { status },
  });
}

export function deleteQuestion(id: string): Promise<void> {
  return request(`${ADMIN}/${id}`, { method: 'DELETE' });
}

// ── Audio (BEHAVIORAL / CORE only) ─────────────────────────────────────────

/**
 * Same-origin URL that streams a question's TTS clip from storage-service.
 * The route is public (anyone with the random object key can play it), so the
 * admin can bind it straight to `<audio src>` without an authed-fetch dance —
 * the same surface the interview flow uses.
 */
export function questionAudioUrl(audioKey: string): string {
  return `${API_BASE_URL}/api/v1/storage/question-audio/${audioKey}`;
}

/**
 * Re-synthesize a question's audio from its current text via TTS and return the
 * new object key. Recovers audio that failed to generate at create time or
 * refreshes a stale clip. Throws on TTS failure so the caller can prompt a retry.
 */
export function regenerateAudio(id: string): Promise<string> {
  return unwrap(`${ADMIN}/${id}/audio/regenerate`, { method: 'POST' });
}

// ── AI generate-testcases (server-side proxy) ──────────────────────────────

/**
 * Generate a coding-question draft via the AI service. The AI/subscription
 * key is a server-only secret — it never touches the browser. This call goes
 * through question-bank-service's admin-gated proxy using the normal
 * JWT-authenticated client (same as every other /admin call); the gateway
 * enforces ROLE_ADMIN. The admin reviews/edits the returned payload before
 * saving via `POST /admin/questions/coding`.
 */
export function generateCoding(
  body: AiGenerateRequest,
): Promise<AiGeneratedCoding> {
  return unwrap(`${ADMIN}/coding/generate`, { method: 'POST', body });
}

const VALID_DIFFICULTY: Difficulty[] = ['EASY', 'MEDIUM', 'HARD'];

/**
 * Map an AI-generated payload onto the QB `CodingQuestionRequest`. The two
 * mismatches the generator/QB schemas have:
 *  - testcases use `isHidden` (AI) vs `is_hidden` (QB entity JSON key)
 *  - generator extras (`label`, `note`) are dropped — QB `TestCase` has no
 *    column for them.
 */
export function mapGeneratedToCodingRequest(
  g: AiGeneratedCoding,
): CodingQuestionRequest {
  const difficulty = (
    VALID_DIFFICULTY.includes(g.difficulty?.toUpperCase() as Difficulty)
      ? g.difficulty.toUpperCase()
      : 'MEDIUM'
  ) as Difficulty;

  return {
    difficulty,
    tags: g.tags ?? [],
    title: g.title ?? '',
    description: g.description ?? '',
    constraints: g.constraints?.trim() ? g.constraints : undefined,
    optimalTimeComplexity: g.optimalTimeComplexity ?? '',
    optimalSpaceComplexity: g.optimalSpaceComplexity ?? '',
    functionMeta: {
      fn: g.functionMeta?.fn ?? '',
      params: g.functionMeta?.params ?? [],
      return: g.functionMeta?.return ?? '',
      orderMatters: !!g.functionMeta?.orderMatters,
      inPlace: !!g.functionMeta?.inPlace,
    },
    starterCode: g.starterCode ?? undefined,
    testCases: (g.testcases ?? []).map((tc) => ({
      id: tc.id,
      inputData: tc.inputData ?? {},
      expectedOutput: tc.expectedOutput ?? {},
      is_hidden: !!tc.isHidden,
      note: tc.note?.trim() ? tc.note : undefined,
    })),
  };
}
