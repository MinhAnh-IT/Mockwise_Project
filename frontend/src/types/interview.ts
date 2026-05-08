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
  isFollowUp: boolean;
  parentSessionQuestionId: string | null;
  text: string;
  audioKey: string | null;
  audioUrl: string | null;
  expectedPoints: string[] | null;
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

export type AnswerView = {
  answerId: string;
  sessionId: string;
  sessionQuestionId: string;
  type: AnswerType;
  status: AnswerStatus;
  score: number | null;
  maxScore: number | null;
  feedback: string | null;
  verdict: Record<string, unknown> | null;
  rubricScores: Record<string, unknown> | null;
  errorCode: string | null;
  errorMessage: string | null;
  submittedAt: string;
  scoredAt: string | null;
};

export type TopicProgress = {
  topicKind: TopicKind;
  topicValue: string;
  status: TopicStatus;
  questionsAsked: number;
  followUpsUsed: number;
  lastScore: number | null;
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
  overallReview: Record<string, unknown> | null;
};
