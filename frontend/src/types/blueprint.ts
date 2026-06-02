/**
 * Interview blueprint admin types + taxonomy helpers.
 *
 * A blueprint is the per-`(targetRole, level, interviewType)` template the
 * interview-service copies into a session at `/start`. The admin console CRUDs
 * them; this module mirrors the backend contract
 * (`interview-service` `BlueprintAdminResponse` / `BlueprintCreateRequest`).
 *
 * Topic taxonomy is reused from `questionBank.ts` so the dropdowns always match
 * the question-bank `Competency`/`Domain` enums (the `topicValue` must match
 * byte-for-byte or `/start` falls into relax → QUESTION_BANK_UNAVAILABLE).
 */
import {
  COMPETENCIES,
  COMPETENCY_LABEL,
  DOMAINS,
  DOMAIN_LABEL,
  type Difficulty,
} from '@/types/questionBank';

export type InterviewType = 'BEHAVIORAL' | 'CORE' | 'CODING';
export type TopicKind = 'COMPETENCY' | 'DOMAIN';
export type Importance = 'HIGH' | 'MED' | 'LOW';
export type Level = 'junior' | 'mid' | 'senior';

export type BlueprintTopic = {
  /** null for CODING slots (backend stores them kind/topicValue null). */
  kind: TopicKind | null;
  topicValue: string | null;
  importance: Importance;
  targetDifficulty: Difficulty;
  orderHint: number;
};

export type Blueprint = {
  id: string;
  targetRole: string;
  level: string;
  interviewType: InterviewType;
  topics: BlueprintTopic[];
  questionBudget: number;
  timeBudgetMinutes: number;
  maxFollowUpsPerTopic: number;
  maxFollowUpsPerSession: number;
  useAiSelector: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export type BlueprintRequest = {
  targetRole: string;
  level: string;
  interviewType: InterviewType;
  topics: BlueprintTopic[];
  questionBudget?: number;
  timeBudgetMinutes?: number;
  maxFollowUpsPerTopic?: number;
  maxFollowUpsPerSession?: number;
  useAiSelector?: boolean;
  isDefault?: boolean;
};

export type BlueprintFilters = {
  targetRole?: string;
  level?: string;
  interviewType?: InterviewType;
  isDefault?: boolean;
};

// ── Labels & option lists ────────────────────────────────────────────────────

export const INTERVIEW_TYPES: InterviewType[] = ['BEHAVIORAL', 'CORE', 'CODING'];
export const INTERVIEW_TYPE_LABEL: Record<InterviewType, string> = {
  BEHAVIORAL: 'Hành vi',
  CORE: 'Chuyên môn',
  CODING: 'Lập trình',
};

export const IMPORTANCES: Importance[] = ['HIGH', 'MED', 'LOW'];
export const IMPORTANCE_LABEL: Record<Importance, string> = {
  HIGH: 'Cao',
  MED: 'Trung bình',
  LOW: 'Thấp',
};

export const LEVELS: Level[] = ['junior', 'mid', 'senior'];
export const LEVEL_LABEL: Record<Level, string> = {
  junior: 'Junior',
  mid: 'Mid',
  senior: 'Senior',
};

/**
 * Canonical normalized role tokens a blueprint is keyed on — these are what
 * `BlueprintNormalizer.normalizeRole` produces, so a blueprint authored with
 * one of these matches at `/start`. (Note: `AI_ML`, not `AI`.)
 */
export const BLUEPRINT_ROLES = [
  'BACKEND',
  'FRONTEND',
  'FULLSTACK',
  'MOBILE',
  'DEVOPS',
  'QA',
  'DATA_ENGINEER',
  'AI_ML',
  'BA',
] as const;
export type BlueprintRole = (typeof BLUEPRINT_ROLES)[number];
export const BLUEPRINT_ROLE_LABEL: Record<BlueprintRole, string> = {
  BACKEND: 'Backend',
  FRONTEND: 'Frontend',
  FULLSTACK: 'Fullstack',
  MOBILE: 'Mobile',
  DEVOPS: 'DevOps',
  QA: 'QA',
  DATA_ENGINEER: 'Data Engineer',
  AI_ML: 'AI / ML Engineer',
  BA: 'Business Analyst',
};

// ── Topic builder config per interview type ──────────────────────────────────

export type TopicConfig = {
  /** Fixed kind for adaptive topics; null for CODING. */
  kind: TopicKind | null;
  options: readonly string[];
  labels: Record<string, string>;
  /** true = pick a competency/domain; false = CODING (difficulty-only slot). */
  structured: boolean;
  /** Vietnamese noun for one row ("chủ đề" vs "slot"). */
  noun: string;
};

/** Map an interview type to its topic-builder configuration. */
export function topicOptionsFor(t: InterviewType): TopicConfig {
  if (t === 'BEHAVIORAL') {
    return {
      kind: 'COMPETENCY',
      options: COMPETENCIES,
      labels: COMPETENCY_LABEL as Record<string, string>,
      structured: true,
      noun: 'chủ đề',
    };
  }
  if (t === 'CORE') {
    return {
      kind: 'DOMAIN',
      options: DOMAINS,
      labels: DOMAIN_LABEL as Record<string, string>,
      structured: true,
      noun: 'chủ đề',
    };
  }
  return { kind: null, options: [], labels: {}, structured: false, noun: 'slot' };
}
