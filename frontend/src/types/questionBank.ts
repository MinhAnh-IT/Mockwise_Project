/**
 * Question-bank admin types — mirrors the BE `question-bank-service` enums,
 * request and response DTOs, plus the AI-service `/generate-testcases`
 * contract (see AI/docs/testcase-generator-design.md).
 *
 * Wire conventions worth remembering:
 * - `FunctionMeta.return` keeps the JSON key `"return"` (judge-service schema).
 * - `TestCase.is_hidden` keeps the snake_case JSON key the QB entity declares
 *   via `@JsonProperty("is_hidden")`. The AI generator instead emits
 *   `isHidden` — `mapGeneratedToCodingRequest` bridges the two.
 */

// ── Enums (string unions mirroring the BE enums) ───────────────────────────

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export type QuestionStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE';

export type QuestionType = 'BEHAVIORAL' | 'CORE_CONCEPTUAL' | 'LIVE_CODING';

export type Competency =
  // Amazon Leadership Principles — primary taxonomy for behavioral blueprints.
  // Must match question-bank `Competency` enum byte-for-byte (cross-service contract).
  | 'CUSTOMER_OBSESSION'
  | 'OWNERSHIP'
  | 'INVENT_AND_SIMPLIFY'
  | 'ARE_RIGHT_A_LOT'
  | 'LEARN_AND_BE_CURIOUS'
  | 'HIRE_AND_DEVELOP_THE_BEST'
  | 'INSIST_ON_HIGHEST_STANDARDS'
  | 'THINK_BIG'
  | 'BIAS_FOR_ACTION'
  | 'FRUGALITY'
  | 'EARN_TRUST'
  | 'DIVE_DEEP'
  | 'HAVE_BACKBONE_DISAGREE_AND_COMMIT'
  | 'DELIVER_RESULTS'
  | 'STRIVE_TO_BE_EARTHS_BEST_EMPLOYER'
  | 'SUCCESS_AND_SCALE_BROAD_RESPONSIBILITY'
  // Legacy generic competencies (pre-LP) — kept so already-seeded questions load.
  | 'CONFLICT_RESOLUTION'
  | 'LEADERSHIP'
  | 'TEAMWORK'
  | 'FAILURE'
  | 'GROWTH'
  | 'COMMUNICATION'
  | 'PRIORITIZATION';

export type Domain =
  | 'OS'
  | 'NETWORKING'
  | 'DATABASE'
  | 'SYSTEM_DESIGN'
  | 'LANGUAGE_SPECIFIC'
  | 'FRAMEWORK'
  | 'SECURITY'
  | 'DESIGN_PATTERN'
  | 'DEVOPS_TOOLS'
  | 'MESSAGING'
  | 'CACHING'
  | 'BUSINESS_ANALYSIS'
  | 'FRONTEND_DEV'
  | 'MOBILE_DEV'
  | 'TESTING'
  | 'DATA_ENGINEERING'
  | 'AI_ML';

export type TargetRole =
  | 'BA'
  | 'BACKEND'
  | 'FRONTEND'
  | 'FULLSTACK'
  | 'MOBILE'
  | 'DEVOPS'
  | 'QA'
  | 'DATA_ENGINEER'
  | 'AI_ML';

/** FE-only tab key. Maps 1:1 onto the three create/list endpoints. */
export type QuestionKind = 'behavioral' | 'core' | 'coding';

// ── Coding sub-shapes (mirror QB entities) ─────────────────────────────────

export type ParamMeta = { name: string; type: string };

export type FunctionMeta = {
  fn: string;
  params: ParamMeta[];
  /** JSON key is literally "return" (judge-service schema). */
  return: string;
  orderMatters: boolean;
  inPlace: boolean;
};

export type StarterCode = {
  java?: string;
  python?: string;
  cpp?: string;
  javascript?: string;
};

export type TestCase = {
  id?: string;
  inputData: Record<string, unknown>;
  expectedOutput: Record<string, unknown>;
  /** snake_case on the wire — QB entity declares @JsonProperty("is_hidden"). */
  is_hidden: boolean;
  /**
   * Optional human explanation (LeetCode "Explanation"). Shown next to the
   * worked example in the candidate workspace; only meaningful on visible
   * (non-hidden) cases. AI-generated cases carry it via the `note` field.
   */
  note?: string | null;
};

// ── Response DTOs ──────────────────────────────────────────────────────────

type QuestionBase = {
  id: string;
  type: QuestionType;
  difficulty: Difficulty;
  status: QuestionStatus;
  tags: string[];
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type BehavioralQuestion = QuestionBase & {
  text: string;
  competency: Competency;
  expectedSignals: string[];
  audioKey?: string | null;
};

export type CoreQuestion = QuestionBase & {
  text: string;
  targetRoles: TargetRole[];
  domain: Domain;
  keyConcepts: string[];
  depthExpected: string;
  audioKey?: string | null;
};

export type CodingQuestion = QuestionBase & {
  title: string;
  description: string;
  /** LeetCode-style constraints, markdown / multi-line. Optional. */
  constraints: string | null;
  optimalTimeComplexity: string;
  optimalSpaceComplexity: string;
  functionMeta: FunctionMeta;
  starterCode?: StarterCode | null;
  testCases: TestCase[];
};

export type AnyQuestion = BehavioralQuestion | CoreQuestion | CodingQuestion;

// ── Request DTOs ───────────────────────────────────────────────────────────

export type BehavioralQuestionRequest = {
  difficulty: Difficulty;
  tags: string[];
  text: string;
  competency: Competency;
  expectedSignals: string[];
};

export type CoreQuestionRequest = {
  difficulty: Difficulty;
  tags: string[];
  text: string;
  targetRoles: TargetRole[];
  domain: Domain;
  keyConcepts: string[];
  depthExpected: string;
};

export type CodingQuestionRequest = {
  difficulty: Difficulty;
  tags: string[];
  title: string;
  description: string;
  /** Optional LeetCode-style constraints, markdown / multi-line. */
  constraints?: string;
  optimalTimeComplexity: string;
  optimalSpaceComplexity: string;
  functionMeta: FunctionMeta;
  starterCode?: StarterCode;
  testCases: TestCase[];
};

// ── List filters ───────────────────────────────────────────────────────────

export type BehavioralFilters = {
  competency?: Competency;
  difficulty?: Difficulty;
  status?: QuestionStatus;
  tags?: string[];
};

export type CoreFilters = {
  domain?: Domain;
  targetRole?: TargetRole;
  difficulty?: Difficulty;
  status?: QuestionStatus;
  tags?: string[];
};

export type CodingFilters = {
  difficulty?: Difficulty;
  status?: QuestionStatus;
  tags?: string[];
};

// ── AI generate-testcases contract ─────────────────────────────────────────

export type AiGenerateMode = 'custom' | 'leetcode';

export type AiGenerateRequest = {
  mode: AiGenerateMode;
  /** mode=leetcode: full URL or bare slug. */
  leetcodeUrl?: string;
  /** mode=custom: title + description required, rest inferred by the AI. */
  title?: string;
  description?: string;
  difficulty?: Difficulty;
  tags?: string[];
  optimalTimeComplexity?: string;
  optimalSpaceComplexity?: string;
  numTestcases: number;
  numVisible: number;
};

export type GeneratedTestCase = {
  id: string;
  label: string;
  inputData: Record<string, unknown>;
  expectedOutput: Record<string, unknown>;
  isHidden: boolean;
  note: string;
};

export type AiGeneratedCoding = {
  title: string;
  description: string;
  difficulty: string;
  tags: string[];
  /** LeetCode-style constraints, markdown / multi-line. "" if none. */
  constraints: string;
  optimalTimeComplexity: string;
  optimalSpaceComplexity: string;
  functionMeta: FunctionMeta;
  starterCode: StarterCode;
  testcases: GeneratedTestCase[];
  meta: {
    mode: string;
    leetcodeNumber?: number | null;
    generatedAt: string;
    modelVersion: string;
    generationDurationMs: number;
    totalTestcases: number;
    visibleTestcases: number;
    hiddenTestcases: number;
  };
  warning?: string | null;
};

// ── Vietnamese labels for the UI ───────────────────────────────────────────

export const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  EASY: 'Dễ',
  MEDIUM: 'Trung bình',
  HARD: 'Khó',
};

export const STATUS_LABEL: Record<QuestionStatus, string> = {
  DRAFT: 'Nháp',
  ACTIVE: 'Đang dùng',
  INACTIVE: 'Tạm ẩn',
};

export const KIND_LABEL: Record<QuestionKind, string> = {
  behavioral: 'Hành vi',
  core: 'Chuyên môn',
  coding: 'Lập trình',
};

export const COMPETENCY_LABEL: Record<Competency, string> = {
  // Amazon Leadership Principles
  CUSTOMER_OBSESSION: 'Ám ảnh vì khách hàng',
  OWNERSHIP: 'Tinh thần làm chủ',
  INVENT_AND_SIMPLIFY: 'Sáng tạo & đơn giản hoá',
  ARE_RIGHT_A_LOT: 'Phán đoán chuẩn xác',
  LEARN_AND_BE_CURIOUS: 'Học hỏi & tò mò',
  HIRE_AND_DEVELOP_THE_BEST: 'Tuyển & phát triển người giỏi',
  INSIST_ON_HIGHEST_STANDARDS: 'Giữ tiêu chuẩn cao nhất',
  THINK_BIG: 'Tư duy lớn',
  BIAS_FOR_ACTION: 'Ưu tiên hành động',
  FRUGALITY: 'Tiết kiệm',
  EARN_TRUST: 'Tạo dựng niềm tin',
  DIVE_DEEP: 'Đào sâu vấn đề',
  HAVE_BACKBONE_DISAGREE_AND_COMMIT: 'Phản biện rồi cam kết',
  DELIVER_RESULTS: 'Tạo ra kết quả',
  STRIVE_TO_BE_EARTHS_BEST_EMPLOYER: 'Môi trường làm việc tốt nhất',
  SUCCESS_AND_SCALE_BROAD_RESPONSIBILITY: 'Thành công & trách nhiệm rộng',
  // Legacy generic competencies
  CONFLICT_RESOLUTION: 'Giải quyết xung đột',
  LEADERSHIP: 'Lãnh đạo',
  TEAMWORK: 'Làm việc nhóm',
  FAILURE: 'Đối mặt thất bại',
  GROWTH: 'Phát triển bản thân',
  COMMUNICATION: 'Giao tiếp',
  PRIORITIZATION: 'Sắp xếp ưu tiên',
};

export const DOMAIN_LABEL: Record<Domain, string> = {
  OS: 'Hệ điều hành',
  NETWORKING: 'Mạng máy tính',
  DATABASE: 'Cơ sở dữ liệu',
  SYSTEM_DESIGN: 'Thiết kế hệ thống',
  LANGUAGE_SPECIFIC: 'Ngôn ngữ lập trình',
  FRAMEWORK: 'Framework',
  SECURITY: 'Bảo mật',
  DESIGN_PATTERN: 'Design pattern',
  DEVOPS_TOOLS: 'DevOps & công cụ',
  MESSAGING: 'Message queue',
  CACHING: 'Caching',
  BUSINESS_ANALYSIS: 'Phân tích nghiệp vụ',
  FRONTEND_DEV: 'Frontend',
  MOBILE_DEV: 'Mobile',
  TESTING: 'Kiểm thử',
  DATA_ENGINEERING: 'Data Engineering',
  AI_ML: 'AI / Machine Learning',
};

export const TARGET_ROLE_LABEL: Record<TargetRole, string> = {
  BA: 'Business Analyst',
  BACKEND: 'Backend',
  FRONTEND: 'Frontend',
  FULLSTACK: 'Fullstack',
  MOBILE: 'Mobile',
  DEVOPS: 'DevOps',
  QA: 'QA',
  DATA_ENGINEER: 'Data Engineer',
  AI_ML: 'AI / ML Engineer',
};

export const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD'];
export const STATUSES: QuestionStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE'];
export const COMPETENCIES = Object.keys(COMPETENCY_LABEL) as Competency[];
export const DOMAINS = Object.keys(DOMAIN_LABEL) as Domain[];
export const TARGET_ROLES = Object.keys(TARGET_ROLE_LABEL) as TargetRole[];

/** Editor language tabs for `StarterCode` (label + object key). */
export const STARTER_LANGS: { key: keyof StarterCode; label: string }[] = [
  { key: 'python', label: 'Python' },
  { key: 'java', label: 'Java' },
  { key: 'cpp', label: 'C++' },
  { key: 'javascript', label: 'JavaScript' },
];
