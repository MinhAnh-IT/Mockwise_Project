// 'CODING' is FE-only until the backend adds InterviewType.CODING + a
// picker path that pins LIVE_CODING questions. See
// frontend/docs/coding-interview-contract.md.
export type InterviewType = 'BEHAVIORAL' | 'CORE' | 'CODING';

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
  // Mid-flight flag: whether this pinned question already has an answer. Lets
  // the session page resume into the correct phase after a reload (answered →
  // "waiting" for the next question, not re-recording). Absent on the start
  // path (treat as not answered).
  answered?: boolean | null;
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
  // The interview is bounded by one session clock (no per-question limit).
  // The FE renders a single global countdown from startedAt → deadlineAt.
  startedAt: string;
  deadlineAt: string;
  firstQuestion: PinnedQuestionView;
};

export type SubmitAnswerInput = {
  type: AnswerType;
  storageObjectId?: string;
  code?: string;
  language?: string;
  // Lever 2 fast path (realtime-stt-plan.md §6.2): a real-time transcript built
  // in the browser while the candidate spoke. When present (+ backend flag on),
  // the answer scores straight off it and the video uploads in the background,
  // attached afterwards via attachVideo(). Absent → legacy upload-then-STT flow.
  transcriptText?: string;
  transcriptLanguage?: string;
  transcriptDurationMs?: number;
  transcriptSource?: string;
};

export type SubmitAnswerOutput = {
  answerId: string;
  status: AnswerStatus;
  submittedAt: string;
  // CODING only: the next problem, pinned synchronously at submit, so the FE
  // advances immediately without polling. Null/absent for adaptive (VIDEO),
  // where the next question is gated on async scoring.
  nextQuestion?: PinnedQuestionView | null;
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

/**
 * Per-dimension score paired with the 1-2 sentence rationale the AI
 * attaches to it. Rendered as a progress bar with the note inline so the
 * candidate can read "specificity = 55" together with *why*.
 */
export type ScoreEntry = {
  score: number | null;
  note: string | null;
};

export type StarComponentItem = {
  detected: boolean;
  /** One of: excellent / good / acceptable / weak / missing — string from AI Quality enum. */
  quality: string | null;
  /** Direct quote from the candidate's transcript (their original language). */
  excerpt: string | null;
};

export type BehavioralEvaluationDetail = {
  kind: 'BEHAVIORAL';
  overallScore: number | null;
  completeness: string | null;
  oneLineVerdict: string | null;
  scores: {
    starStructure: ScoreEntry | null;
    relevance: ScoreEntry | null;
    specificity: ScoreEntry | null;
    impactResult: ScoreEntry | null;
    selfAwareness: ScoreEntry | null;
  } | null;
  starBreakdown: {
    situation: StarComponentItem | null;
    task: StarComponentItem | null;
    action: StarComponentItem | null;
    result: StarComponentItem | null;
  } | null;
  signalCoverage: { signalName: string; detected: boolean; evidence: string | null }[];
  redFlags: { type: string; severity: string; detail: string | null }[];
  feedback: {
    strengths: string[];
    improvements: string[];
    sampleStrongerAnswerStructure: string | null;
  } | null;
};

export type ConceptualEvaluationDetail = {
  kind: 'CORE_CONCEPTUAL';
  overallScore: number | null;
  completeness: string | null;
  oneLineVerdict: string | null;
  scores: {
    accuracy: ScoreEntry | null;
    depth: ScoreEntry | null;
    practicalApplication: ScoreEntry | null;
    clarity: ScoreEntry | null;
  } | null;
  conceptCoverage: {
    conceptName: string;
    mentioned: boolean;
    correct: boolean | null;
    candidateStatement: string | null;
    correction: string | null;
  }[];
  levelCalibration: {
    expectedLevel: string | null;
    actualDemonstratedLevel: string | null;
    gap: string | null;
  } | null;
  misconceptions: { claim: string; correction: string | null }[];
  feedback: {
    strengths: string[];
    improvements: string[];
    keyPointsToStudy: string[];
  } | null;
};

export type LiveCodingEvaluationDetail = {
  kind: 'LIVE_CODING';
  overallScore: number | null;
  completeness: string | null;
  oneLineVerdict: string | null;
  scores: {
    timeComplexity: ScoreEntry | null;
    spaceComplexity: ScoreEntry | null;
    codeQuality: ScoreEntry | null;
    problemSolving: ScoreEntry | null;
  } | null;
  detectedComplexity: { time: string | null; space: string | null } | null;
  optimalComplexity: { time: string | null; space: string | null } | null;
  isOptimal: boolean | null;
  codeIssues: { type: string; line: number | null; detail: string }[];
  feedback: {
    strengths: string[];
    improvements: string[];
    optimizationHint: string | null;
    sampleOptimalSolution: string | null;
  } | null;
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
  // VIDEO answers only, revealed once SCORED: the transcript that actually
  // scored this answer (real-time on the fast path, else batch STT). Shown on
  // the report so the candidate reads the same text the AI graded.
  transcript?: string | null;
  // CODE answers only, revealed once the session is SCORED: the candidate's
  // submitted source + the judge's per-case roster. Null/absent for VIDEO
  // answers and while the session is still in progress.
  coding?: {
    code: string | null;
    language: string | null;
    judgeVerdict: string | null;
    testsPassed: number | null;
    testsTotal: number | null;
    cases: { testCaseId: string; status: string }[];
  } | null;
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
  // startedAt + timeBudgetMinutes — the interview's single deadline.
  deadlineAt: string | null;
  finishedAt: string | null;
  scoredAt: string | null;
  topicProgress: TopicProgress[];
  questions: PinnedQuestionView[];
  overallReview: OverallReviewView | null;
};

// ── Admin oversight (read-only) ──────────────────────────────────────────────

/**
 * One row of the admin session list (AdminSessionResponse). Shallow by design:
 * lifecycle + scoring metadata only, never the answers/questions — admins
 * oversee and report, they do not inspect a session's contents.
 */
export type AdminSession = {
  id: string;
  userId: string;
  targetRole: string | null;
  level: string | null;
  interviewType: InterviewType | null;
  status: SessionStatus;
  questionCount: number;
  finalScore: number | null;
  startedAt: string | null;
  finishedAt: string | null;
  scoredAt: string | null;
  createdAt: string;
};

export type AdminSessionFilters = {
  userId?: string;
  status?: SessionStatus;
  interviewType?: InterviewType;
  targetRole?: string;
  level?: string;
  from?: string;
  to?: string;
};

/** Mirrors AdminSessionStatsResponse. */
export type InterviewSessionStats = {
  totalSessions: number;
  byStatus: Record<string, number>;
  byType: Record<string, number>;
  averageScore: number | null;
};
