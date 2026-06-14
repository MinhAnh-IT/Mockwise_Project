/** Types for the admin Judge-monitoring dashboard (served by judge-service). */

export type JobStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export type JudgeStats = {
  window: string;
  since: string;
  total: number;
  byStatus: Record<string, number>;
  byVerdict: Record<string, number>;
  backlog: number;
  avgLatencyMs: number | null;
  p95LatencyMs: number | null;
  retriedJobs: number;
  maxRetryCount: number;
  generatedAt: string;
};

export type JudgeJobSummary = {
  id: string;
  submissionId: string;
  origin: string | null;
  status: JobStatus;
  verdict: string | null;
  language: string;
  doneCases: number;
  totalCases: number;
  retryCount: number;
  createdAt: string | null;
  finishedAt: string | null;
  latencyMs: number | null;
};

export type TaskResultView = {
  testCaseId: string;
  orderIndex: number;
  status: string;
  judge0Token: string | null;
  runtimeMs: number | null;
  memoryKb: number | null;
  stdout: string | null;
  stderr: string | null;
  finishedAt: string | null;
};

export type JudgeJobDetail = {
  job: JudgeJobSummary;
  judge0Token: string | null;
  results: TaskResultView[];
};

export type JudgeJobPage = {
  content: JudgeJobSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type DlqMessage = {
  partition: number;
  offset: number;
  timestamp: string | null;
  originalTopic: string | null;
  originalPartition: number | null;
  originalOffset: number | null;
  exceptionClass: string | null;
  exceptionMessage: string | null;
  payloadPreview: string | null;
};

export type DlqOverview = {
  topic: string;
  reachable: boolean;
  total: number;
  messages: DlqMessage[];
  note: string | null;
};

export type Judge0Health = {
  reachable: boolean;
  latencyMs: number | null;
  version: string | null;
  queueSize: number | null;
  workersTotal: number | null;
  workersIdle: number | null;
  workersWorking: number | null;
  detail: string | null;
};
