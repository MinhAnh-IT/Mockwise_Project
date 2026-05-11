export type InterviewType = 'BEHAVIORAL' | 'CORE' | 'MIXED';

export type SessionStatus =
  | 'CREATED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'SCORED';

export type AnswerStatus =
  | 'SUBMITTED'
  | 'PROCESSING'
  | 'READY'
  | 'EVALUATING'
  | 'SCORED'
  | 'FAILED';

export type AnswerType = 'VIDEO' | 'CODE';

export type QuestionType = 'BEHAVIORAL' | 'CORE_CONCEPTUAL' | 'LIVE_CODING';

export type QuestionSource = 'BANK' | 'AI_GENERATED' | 'PRE_AUTHORED';

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export type TopicKind = 'COMPETENCY' | 'DOMAIN';

export type TopicStatus =
  | 'NOT_TESTED'
  | 'PROBING'
  | 'STRONG'
  | 'ADEQUATE'
  | 'PARTIAL'
  | 'WEAK'
  | 'UNKNOWN';

// Backend redacts rubric/classification fields while a session is in flight
// (see PinnedQuestionView#redacted on the server). Only the post-SCORED
// report response carries the full set, so all of those fields are nullable
// here and the UI must guard against null before rendering them.
export type PinnedQuestionView = {
  sessionQuestionId: string;
  sequence: number;
  questionId: string | null;
  questionType: QuestionType;
  topicKind: TopicKind | null;
  topicValue: string | null;
  difficulty: Difficulty | null;
  source: QuestionSource | null;
  isFollowUp: boolean | null;
  parentSessionQuestionId: string | null;
  text: string;
  audioKey: string | null;
  audioUrl: string | null;
  expectedPoints: string[] | null;
  // Populated only on the SCORED report response so the FE can lazily
  // fetch the per-question verdict + media replay. Null mid-flight (the
  // candidate doesn't see their own past answers until scoring completes)
  // and on follow-up questions that were skipped.
  latestAnswerId: string | null;
  // Embedded answer payload (score / verdict / feedback / signed video URL)
  // — populated only on the SCORED report response so the FE can render the
  // per-question card without a follow-up call to /answers/{aid}. Same null
  // semantics as latestAnswerId.
  answer: AnswerView | null;
};

export type StartSessionInput = {
  interviewType: InterviewType;
  timeBudgetMinutesOverride?: number;
};

export type StartSessionOutput = {
  sessionId: string;
  targetRole: string;
  level: string;
  interviewType: InterviewType;
  questionBudget: number;
  timeBudgetMinutes: number;
  firstQuestion: PinnedQuestionView;
};

export type SubmitAnswerInput = {
  type: AnswerType;
  storageObjectId?: string;
  code?: string;
  language?: string;
};

export type SubmitAnswerOutput = {
  answerId: string;
  status: AnswerStatus;
  submittedAt: string;
};

/**
 * Strongly-typed mirror of the BE {@code AssessmentVerdict} record. Replaces
 * the previous free-form {@code Record<string, unknown>} so the report page
 * can read fields directly without runtime guards.
 */
export type AssessmentVerdict = {
  scoreNormalized: number | null;
  hireSignal: string | null;
  grade: string | null;
  signalStrength: string | null;
  completeness: string | null;
  correctness: string | null;
  depth: string | null;
  weakTargets: unknown[] | null;
  strongTargets: unknown[] | null;
};

/**
 * Curated, user-facing projection of the AI evaluator output (mirrors the BE
 * {@code EvaluationDetail} sealed interface). Discriminated by {@code kind}
 * which matches the parent question's {@code QuestionType} one-for-one, so
 * the UI can pattern-match without a separate lookup.
 *
 * <p>Only fields with a sensible presentation are exposed here — internal
 * planner / meta fields stay on the row's {@code raw_evaluation} blob.
 */
export type EvaluationDetail =
  | BehavioralEvaluationDetail
  | ConceptualEvaluationDetail
  | LiveCodingEvaluationDetail;

export type BehavioralEvaluationDetail = {
  kind: 'BEHAVIORAL';
  overallScore: number | null;
  completeness: string | null;
  scores: {
    starStructure: number | null;
    relevance: number | null;
    specificity: number | null;
    impactResult: number | null;
    selfAwareness: number | null;
  } | null;
  signalCoverage: { signalName: string; detected: boolean }[];
  redFlags: { type: string; severity: string }[];
};

export type ConceptualEvaluationDetail = {
  kind: 'CORE_CONCEPTUAL';
  overallScore: number | null;
  completeness: string | null;
  scores: {
    accuracy: number | null;
    depth: number | null;
    practicalApplication: number | null;
    clarity: number | null;
  } | null;
  conceptCoverage: { conceptName: string; mentioned: boolean; correct: boolean | null }[];
  misconceptions: { claim: string }[];
};

export type LiveCodingEvaluationDetail = {
  kind: 'LIVE_CODING';
  overallScore: number | null;
  completeness: string | null;
  scores: {
    timeComplexity: number | null;
    spaceComplexity: number | null;
    codeQuality: number | null;
    problemSolving: number | null;
  } | null;
  isOptimal: boolean | null;
  codeIssues: { type: string; detail: string }[];
};

export type AnswerView = {
  answerId: string;
  sessionId: string;
  sessionQuestionId: string;
  type: AnswerType;
  status: AnswerStatus;
  score: number | null;
  maxScore: number | null;
  feedback: string | null;
  verdict: AssessmentVerdict | null;
  evaluationDetail: EvaluationDetail | null;
  errorCode: string | null;
  errorMessage: string | null;
  submittedAt: string;
  scoredAt: string | null;
  // Presigned MinIO URL for the candidate's submitted video, served via the
  // nginx /minio/ proxy. Null until the session reaches SCORED, and null for
  // CODE answers regardless. Bind straight to <video src> — auth is in the
  // signed query params, no Authorization header needed. URL expires per the
  // server's download-ttl-seconds; re-fetch the session to get a fresh one.
  storageObjectId: string | null;
  mediaUrl: string | null;
};

export type TopicProgress = {
  topicKind: TopicKind;
  topicValue: string;
  status: TopicStatus;
  questionsAsked: number;
  followUpsUsed: number;
  lastScore: number | null;
};

/**
 * Slim per-card projection for the history page. Backend trims topic /
 * question / overall-review detail; full data is loaded via getSession on
 * click. {@code hireSignal} / {@code grade} are extracted server-side from
 * the overallReview JSON when the session has reached SCORED, null otherwise.
 */
export type SessionSummaryView = {
  sessionId: string;
  targetRole: string;
  level: string;
  interviewType: InterviewType;
  status: SessionStatus;
  questionCount: number;
  timeBudgetMinutes: number;
  finalScore: number | null;
  startedAt: string | null;
  finishedAt: string | null;
  scoredAt: string | null;
  createdAt: string;
  hireSignal: string | null;
  grade: string | null;
};

/**
 * Mirror of the shared `com.core.apiresponse.response.ApiListResponse`
 * envelope used by the BE for list endpoints. Carries the current page of
 * items plus the server-side total — the FE derives "has next" from
 * (running offset) vs totalCount.
 */
export type ApiListResponse<T> = {
  totalCount: number | null;
  items: T[];
};

/**
 * Strongly-typed mirror of the BE {@code OverallReviewView}. Replaces the
 * previous free-form map so the report page can read fields directly.
 */
export type TopicReviewItem = {
  topicKind: string | null;
  topicValue: string | null;
  status: string | null;
  comment: string | null;
};

export type OverallReviewView = {
  sessionId: string | null;
  overallScore: number | null;
  grade: string | null;
  hireSignal: string | null;
  summary: string | null;
  strengths: string[] | null;
  weaknesses: string[] | null;
  perTopicSummary: TopicReviewItem[] | null;
  recommendations: string[] | null;
};

export type SessionView = {
  sessionId: string;
  userId: string;
  targetRole: string;
  level: string;
  interviewType: InterviewType;
  status: SessionStatus;
  questionCount: number;
  timeBudgetMinutes: number;
  finalScore: number | null;
  startedAt: string;
  finishedAt: string | null;
  scoredAt: string | null;
  topicProgress: TopicProgress[];
  questions: PinnedQuestionView[];
  overallReview: OverallReviewView | null;
};
