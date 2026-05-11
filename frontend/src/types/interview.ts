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
  errorCode: string | null;
  errorMessage: string | null;
  submittedAt: string;
  scoredAt: string | null;
  // Same-origin path to the candidate's submitted video. Null until the
  // session reaches SCORED, and null for CODE answers regardless. The path
  // requires a JWT — fetch via fetchAuthedBlobUrl, then bind to <video src>.
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
