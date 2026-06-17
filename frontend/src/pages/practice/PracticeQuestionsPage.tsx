import { Children, useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  Award,
  BookOpen,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Code2,
  Layers,
  Lightbulb,
  Loader2,
  MessageSquare,
  Quote,
  Sparkles,
  Target,
  Video as VideoIcon,
  Volume2,
  Zap,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getSession } from '@/api/interviews';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import { findPracticeOption } from '@/data/practice';
import type {
  AnswerView,
  AssessmentVerdict,
  BehavioralEvaluationDetail,
  ConceptualEvaluationDetail,
  EvaluationDetail,
  LiveCodingEvaluationDetail,
  PinnedQuestionView,
  ScoreEntry,
  SessionView,
  StarComponentItem,
} from '@/types/interview';

const POLL_INTERVAL_MS = 3000;
const MAX_POLL_ATTEMPTS = 60;

/**
 * Per-question detail. Paginated 1-question-per-page with Prev/Next plus a
 * sticky question navigator on the left. Sibling of {@code
 * PracticeReportPage} which shows the overall summary.
 */
export default function PracticeQuestionsPage() {
  const { type, sid } = useParams<{ type: string; sid: string }>();
  const navigate = useNavigate();
  const option = type ? findPracticeOption(type) : undefined;

  const [session, setSession] = useState<SessionView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const attemptsRef = useRef(0);

  // Refetches the session in-place. Used both by the post-recording poll
  // loop (while we wait for the session to reach SCORED) and by the video
  // <retry> button — a presigned URL that expired between page-load and
  // playback is fixed by simply re-fetching, since the BE re-signs on each
  // GET /interviews/{sid}.
  const loadSession = useCallback(async () => {
    if (!sid) return null;
    const s = await getSession(sid);
    setSession(s);
    return s;
  }, [sid]);

  useEffect(() => {
    if (!sid) return;
    let cancelled = false;
    let timer: number | null = null;

    const tick = async () => {
      try {
        const s = await loadSession();
        if (cancelled || s === null) return;
        if (s.status === 'SCORED') return;
        attemptsRef.current += 1;
        if (attemptsRef.current >= MAX_POLL_ATTEMPTS) {
          setError('Hệ thống chấm điểm đang chậm hơn dự kiến. Bạn có thể quay lại sau.');
          return;
        }
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) return;
        setError(err instanceof Error ? err.message : 'Không tải được chi tiết phiên.');
        return;
      }
      if (!cancelled) timer = window.setTimeout(tick, POLL_INTERVAL_MS);
    };

    tick();
    return () => {
      cancelled = true;
      if (timer !== null) window.clearTimeout(timer);
    };
  }, [sid, loadSession]);

  if (!option || !sid) {
    navigate('/practice', { replace: true });
    return null;
  }

  const isScored = session?.status === 'SCORED';
  const reportHref = `/practice/${type}/session/${sid}/report`;

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />

      <main className="flex-1 px-4 md:px-8 pt-24 pb-16">
        <div className="max-w-[1400px] mx-auto">
          <Link
            to={reportHref}
            className="inline-flex items-center gap-1 text-sm font-medium text-on-surface-variant hover:text-on-surface mb-4 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Về trang tổng quan
          </Link>

          <PageTitle option={option} session={session} />

          {!isScored && (
            <StatusPlaceholder status={session?.status} error={error} />
          )}

          {isScored && session && session.questions.length > 0 && (
            <QuestionPager
              questions={session.questions}
              onReload={loadSession}
            />
          )}

          {isScored && session && session.questions.length === 0 && (
            <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-10 text-center">
              <p className="text-sm text-on-surface-variant">
                Phiên này chưa có câu hỏi nào.
              </p>
            </div>
          )}
        </div>
      </main>

      <Footer />
    </div>
  );
}

function PageTitle({
  option,
  session,
}: {
  option: { title: string };
  session: SessionView | null;
}) {
  return (
    <div className="mb-5">
      <p className="text-[10px] font-bold uppercase tracking-widest text-secondary mb-2">
        Chi tiết câu hỏi
      </p>
      <h1 className="text-2xl md:text-3xl font-extrabold text-on-surface tracking-tight">
        {option.title}
      </h1>
      {session && (
        <p className="mt-1 text-sm text-on-surface-variant">
          {session.targetRole} · {session.level} · {session.questionCount} câu
        </p>
      )}
    </div>
  );
}

function StatusPlaceholder({
  status,
  error,
}: {
  status: SessionView['status'] | undefined;
  error: string | null;
}) {
  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-2xl p-6 text-center">
        <p className="text-sm text-red-700 font-semibold mb-2">
          Có lỗi tải chi tiết
        </p>
        <p className="text-xs text-red-600 leading-relaxed">{error}</p>
      </div>
    );
  }
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-3xl p-10 md:p-14 text-center">
      <div className="w-16 h-16 mx-auto mb-5 rounded-2xl bg-secondary/10 text-secondary flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin" />
      </div>
      <h3 className="text-lg font-bold text-on-surface mb-2">
        {status === 'COMPLETED' || status === 'CANCELLED'
          ? 'Đang tổng hợp chi tiết…'
          : 'Đang chấm các câu trả lời…'}
      </h3>
      <p className="text-sm text-on-surface-variant max-w-md mx-auto leading-relaxed">
        Vui lòng quay lại sau khi hệ thống chấm xong.
      </p>
    </div>
  );
}

function QuestionPager({
  questions,
  onReload,
}: {
  questions: PinnedQuestionView[];
  onReload: () => Promise<SessionView | null>;
}) {
  const [index, setIndex] = useState(0);
  const safeIndex = Math.min(index, questions.length - 1);
  const current = questions[safeIndex];
  const total = questions.length;
  const goPrev = () => setIndex((i) => Math.max(0, i - 1));
  const goNext = () => setIndex((i) => Math.min(total - 1, i + 1));
  const progressPct = ((safeIndex + 1) / total) * 100;

  return (
    <div className="space-y-5">
      <QuestionNav
        questions={questions}
        activeIndex={safeIndex}
        onSelect={setIndex}
        progressPct={progressPct}
        onPrev={goPrev}
        onNext={goNext}
      />

      <QuestionPanel question={current} onReload={onReload} />

      <div className="flex items-center justify-between gap-3 pt-2">
        <button
          type="button"
          onClick={goPrev}
          disabled={safeIndex === 0}
          className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-outline-variant bg-surface-container-lowest text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <ChevronLeft className="w-4 h-4" />
          Câu trước
        </button>
        <span className="text-xs font-semibold text-on-surface-variant tabular-nums">
          {safeIndex + 1} / {total}
        </span>
        <button
          type="button"
          onClick={goNext}
          disabled={safeIndex >= total - 1}
          className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-secondary text-on-secondary text-sm font-semibold hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Câu tiếp
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

function QuestionNav({
  questions,
  activeIndex,
  onSelect,
  progressPct,
  onPrev,
  onNext,
}: {
  questions: PinnedQuestionView[];
  activeIndex: number;
  onSelect: (i: number) => void;
  progressPct: number;
  onPrev: () => void;
  onNext: () => void;
}) {
  const total = questions.length;
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-4 sticky top-20 z-30 backdrop-blur-md bg-surface-container-lowest/90">
      <div className="flex items-center justify-between gap-3 mb-3">
        <p className="text-sm font-bold text-on-surface">
          Câu <span className="tabular-nums">{activeIndex + 1}</span>{' '}
          <span className="text-on-surface-variant font-medium">/ {total}</span>
        </p>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onPrev}
            disabled={activeIndex === 0}
            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-outline-variant text-xs font-semibold text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <ChevronLeft className="w-3.5 h-3.5" />
            Trước
          </button>
          <button
            type="button"
            onClick={onNext}
            disabled={activeIndex >= total - 1}
            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-secondary text-on-secondary text-xs font-semibold hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Tiếp
            <ChevronRight className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <div className="flex items-center gap-1.5 overflow-x-auto pb-1 -mx-1 px-1 mb-3">
        {questions.map((q, i) => {
          const active = i === activeIndex;
          const score = q.answer?.score;
          const hasAnswer = !!(q.answer ?? q.latestAnswerId);
          const tone = scoreTone(typeof score === 'number' ? score : null);
          return (
            <button
              type="button"
              key={q.sessionQuestionId}
              onClick={() => onSelect(i)}
              className={`shrink-0 inline-flex items-center gap-1.5 px-2.5 h-9 rounded-xl border text-xs font-bold transition-colors ${
                active
                  ? 'bg-secondary text-on-secondary border-secondary'
                  : 'bg-surface-container-lowest text-on-surface border-outline-variant hover:bg-surface-container-low'
              }`}
              title={q.text}
            >
              <span className="tabular-nums">{q.sequence}</span>
              {typeof score === 'number' ? (
                <span
                  className={`tabular-nums text-[10px] font-extrabold px-1 py-0.5 rounded ${
                    active
                      ? 'bg-on-secondary/15 text-on-secondary'
                      : `${tone.text} bg-surface-container`
                  }`}
                >
                  {score.toFixed(1)}
                </span>
              ) : !hasAnswer ? (
                <span
                  className={`text-[10px] font-semibold px-1 py-0.5 rounded ${
                    active
                      ? 'bg-on-secondary/15 text-on-secondary'
                      : 'bg-surface-container text-on-surface-variant'
                  }`}
                >
                  bỏ
                </span>
              ) : null}
              {q.isFollowUp && (
                <span
                  className={`text-[9px] font-bold uppercase tracking-wider ${
                    active ? 'text-on-secondary/80' : 'text-on-surface-variant'
                  }`}
                >
                  nối
                </span>
              )}
            </button>
          );
        })}
      </div>

      <div className="h-1 rounded-full bg-surface-container overflow-hidden">
        <div
          className="h-full bg-secondary transition-all"
          style={{ width: `${progressPct}%` }}
        />
      </div>
    </div>
  );
}

function QuestionPanel({
  question,
  onReload,
}: {
  question: PinnedQuestionView;
  onReload: () => Promise<SessionView | null>;
}) {
  const answer = question.answer;
  const hasAnswer = !!(answer ?? question.latestAnswerId);

  return (
    <div className="space-y-4">
      <QuestionCard question={question} />
      {hasAnswer ? (
        answer ? (
          <AnswerDetail answer={answer} onReload={onReload} />
        ) : (
          <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6 text-center text-xs text-on-surface-variant italic">
            Đang chuẩn bị chi tiết câu trả lời…
          </div>
        )
      ) : (
        <div className="bg-surface-container-lowest border border-dashed border-outline-variant rounded-2xl p-8 text-center">
          <div className="w-12 h-12 mx-auto mb-3 rounded-2xl bg-surface-container text-on-surface-variant flex items-center justify-center">
            <MessageSquare className="w-6 h-6" />
          </div>
          <p className="text-sm font-semibold text-on-surface mb-1">
            Không có câu trả lời
          </p>
          <p className="text-xs text-on-surface-variant">
            Ứng viên không trả lời câu này.
          </p>
        </div>
      )}
    </div>
  );
}

function QuestionCard({ question }: { question: PinnedQuestionView }) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl overflow-hidden">
      <div className="px-5 py-4 bg-secondary/5 border-b border-outline-variant/60">
        <div className="flex items-center gap-2 mb-3 flex-wrap">
          <span className="text-[10px] font-bold uppercase tracking-widest text-secondary bg-secondary/10 px-2 py-1 rounded-md">
            Câu hỏi #{question.sequence}
          </span>
          {question.topicValue && (
            <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-widest text-on-surface-variant bg-surface-container-lowest border border-outline-variant px-2 py-1 rounded-md">
              <Target className="w-3 h-3" />
              {question.topicValue.replace(/_/g, ' ')}
            </span>
          )}
          {question.isFollowUp && (
            <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant bg-surface-container px-2 py-1 rounded-md">
              Nối tiếp
            </span>
          )}
          {question.difficulty && (
            <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant px-2 py-1 rounded-md border border-outline-variant">
              {question.difficulty}
            </span>
          )}
        </div>
        <p className="text-base md:text-lg font-semibold text-on-surface leading-relaxed">
          {question.text}
        </p>
      </div>
      {question.audioUrl && (
        <div className="px-5 py-3 bg-surface-container-low/40">
          <p className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-2">
            <Volume2 className="w-3.5 h-3.5" />
            Nghe lại câu hỏi
          </p>
          <audio src={question.audioUrl} controls className="w-full" />
        </div>
      )}
    </div>
  );
}

const CASE_STATUS_STYLE: Record<string, string> = {
  AC: 'bg-emerald-100 text-emerald-700',
  WA: 'bg-red-100 text-red-700',
  TLE: 'bg-amber-100 text-amber-700',
  MLE: 'bg-amber-100 text-amber-700',
  RE: 'bg-red-100 text-red-700',
  CE: 'bg-red-100 text-red-700',
};

/**
 * Candidate's submitted code + the judge's per-case roster for a
 * LIVE_CODING answer. Rendered in the post-session report so the reviewer
 * can see exactly what was run and which test cases passed.
 */
function CodingSubmission({
  coding,
}: {
  coding: NonNullable<AnswerView['coding']>;
}) {
  const passed = coding.testsPassed ?? 0;
  const total = coding.testsTotal ?? coding.cases.length;
  const allPass = total > 0 && passed === total;
  return (
    <Card icon={<Code2 className="w-4 h-4" />} title="Bài làm của bạn">
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-2 text-xs">
          {coding.language && (
            <span className="font-semibold uppercase tracking-wide bg-surface-container px-2 py-1 rounded">
              {coding.language}
            </span>
          )}
          {coding.judgeVerdict && (
            <span
              className={`font-bold px-2 py-1 rounded ${
                allPass
                  ? 'bg-emerald-100 text-emerald-700'
                  : 'bg-red-100 text-red-700'
              }`}
            >
              {coding.judgeVerdict}
            </span>
          )}
          {total > 0 && (
            <span
              className={`font-semibold flex items-center gap-1 ${
                allPass ? 'text-emerald-700' : 'text-on-surface-variant'
              }`}
            >
              <CheckCircle2 className="w-3.5 h-3.5" />
              Đã pass {passed}/{total} testcase
            </span>
          )}
        </div>

        {coding.cases.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {coding.cases.map((c, i) => {
              const st = (c.status || '').toUpperCase();
              const cls =
                CASE_STATUS_STYLE[st] ??
                'bg-surface-container text-on-surface-variant';
              return (
                <span
                  key={c.testCaseId || i}
                  className={`text-[11px] font-semibold px-2 py-1 rounded ${cls}`}
                  title={`Test ${i + 1}: ${st}`}
                >
                  #{i + 1} {st}
                </span>
              );
            })}
          </div>
        )}

        {coding.code ? (
          <pre className="text-[12px] leading-relaxed bg-on-surface text-inverse-on-surface rounded-lg p-4 overflow-x-auto font-mono whitespace-pre">
            {coding.code}
          </pre>
        ) : (
          <p className="text-xs text-on-surface-variant">
            Ứng viên không nộp code cho câu này.
          </p>
        )}
      </div>
    </Card>
  );
}

function AnswerDetail({
  answer,
  onReload,
}: {
  answer: AnswerView;
  onReload: () => Promise<SessionView | null>;
}) {
  const detail = answer.evaluationDetail;
  const oneLineVerdict = detail?.oneLineVerdict ?? null;
  const hasVideo = answer.type === 'VIDEO' && !!answer.mediaUrl;

  return (
    <div className="space-y-4">
      {answer.status === 'FAILED' && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 flex items-start gap-2.5">
          <AlertTriangle className="w-4 h-4 text-red-600 mt-0.5 shrink-0" />
          <p className="text-xs text-red-700 leading-relaxed">
            Câu này không chấm được
            {answer.errorMessage ? `: ${answer.errorMessage}` : '.'}
          </p>
        </div>
      )}

      <CardRow>
        <ScoreHero
          score={answer.score}
          maxScore={answer.maxScore ?? 10}
          verdict={answer.verdict}
          oneLineVerdict={oneLineVerdict}
        />
        {hasVideo && answer.mediaUrl && (
          <Card icon={<VideoIcon className="w-4 h-4" />} title="Video bạn đã quay">
            <AnswerVideo mediaUrl={answer.mediaUrl} onReload={onReload} />
          </Card>
        )}
      </CardRow>

      {answer.type === 'VIDEO' && answer.transcript && (
        <Card icon={<Quote className="w-4 h-4" />} title="Nội dung câu trả lời (đã dùng để chấm)">
          <p className="text-sm text-on-surface-variant leading-relaxed whitespace-pre-line">
            {answer.transcript}
          </p>
        </Card>
      )}

      {answer.feedback && (
        <Card
          icon={<MessageSquare className="w-4 h-4" />}
          title="Phản hồi tổng quan"
        >
          <p className="text-sm text-on-surface leading-relaxed whitespace-pre-line">
            {answer.feedback}
          </p>
        </Card>
      )}

      {answer.coding && <CodingSubmission coding={answer.coding} />}

      <EvaluationBreakdown detail={detail} />
    </div>
  );
}

function ScoreHero({
  score,
  maxScore,
  verdict,
  oneLineVerdict,
}: {
  score: number | null;
  maxScore: number;
  verdict: AssessmentVerdict | null;
  oneLineVerdict: string | null;
}) {
  const tone = scoreTone(typeof score === 'number' ? score : null);
  const pct =
    typeof score === 'number'
      ? Math.max(0, Math.min(100, (score / maxScore) * 100))
      : null;

  return (
    <div className="relative overflow-hidden bg-gradient-to-br from-secondary-fixed/40 via-surface-container-lowest to-surface-container-low border border-outline-variant rounded-2xl p-5 md:p-6">
      <div className="absolute -right-16 -top-16 w-48 h-48 rounded-full bg-secondary/10 blur-3xl pointer-events-none" />

      <div className="relative">
        <div className="flex items-center gap-4 mb-3">
          {typeof score === 'number' ? (
            <div className="flex items-baseline gap-1">
              <span className={`text-4xl md:text-5xl font-extrabold leading-none ${tone.text}`}>
                {score.toFixed(1)}
              </span>
              <span className="text-sm text-on-surface-variant font-semibold">
                / {maxScore.toFixed(0)}
              </span>
            </div>
          ) : (
            <span className="text-sm text-on-surface-variant italic">
              Chưa có điểm
            </span>
          )}
          {pct !== null && (
            <div className="flex-1 max-w-xs h-2 rounded-full bg-surface-container overflow-hidden">
              <div
                className={`h-full rounded-full ${tone.bar}`}
                style={{ width: `${pct}%` }}
              />
            </div>
          )}
        </div>

        {oneLineVerdict && (
          <div className="relative mb-3">
            <Quote className="absolute -left-1 -top-2 w-4 h-4 text-secondary/30" />
            <p className="pl-4 text-sm text-on-surface leading-relaxed italic">
              {oneLineVerdict}
            </p>
          </div>
        )}

        <VerdictChips verdict={verdict} />
      </div>
    </div>
  );
}

function scoreTone(score: number | null) {
  if (typeof score !== 'number') {
    return {
      ring: 'text-on-surface-variant/30',
      text: 'text-on-surface-variant',
      bar: 'bg-on-surface-variant/30',
    };
  }
  if (score >= 8)
    return { ring: 'text-emerald-500', text: 'text-emerald-700', bar: 'bg-emerald-500' };
  if (score >= 6.5)
    return { ring: 'text-secondary', text: 'text-secondary', bar: 'bg-secondary' };
  if (score >= 5)
    return { ring: 'text-amber-500', text: 'text-amber-700', bar: 'bg-amber-500' };
  return { ring: 'text-red-500', text: 'text-red-700', bar: 'bg-red-500' };
}

const VERDICT_LABELS: Record<string, Record<string, string>> = {
  signalStrength: {
    NONE: 'Không thấy tín hiệu',
    PARTIAL: 'Một phần',
    ADEQUATE: 'Đạt yêu cầu',
    STRONG: 'Mạnh',
  },
  completeness: {
    NO_ANSWER: 'Không trả lời',
    INCOMPLETE: 'Chưa đầy đủ',
    COMPLETE: 'Đầy đủ',
  },
  correctness: {
    WRONG: 'Sai',
    MIXED: 'Pha trộn',
    CORRECT: 'Chính xác',
  },
  depth: {
    SURFACE: 'Bề mặt',
    MODERATE: 'Vừa phải',
    DEEP: 'Sâu',
  },
};

const VERDICT_LABEL_TITLES: Record<string, string> = {
  signalStrength: 'Tín hiệu',
  completeness: 'Đầy đủ',
  correctness: 'Đúng/sai',
  depth: 'Độ sâu',
};

type VerdictChipKey = 'signalStrength' | 'completeness' | 'correctness' | 'depth';
type VerdictChip = { key: VerdictChipKey; title: string; label: string };

function VerdictChips({ verdict }: { verdict: AssessmentVerdict | null }) {
  if (!verdict) return null;
  const titleEntries = Object.entries(VERDICT_LABEL_TITLES) as Array<[VerdictChipKey, string]>;
  const entries: VerdictChip[] = titleEntries.flatMap(([key, title]) => {
    const raw = verdict[key];
    if (typeof raw !== 'string') return [];
    const label = VERDICT_LABELS[key]?.[raw] ?? raw;
    return [{ key, title, label }];
  });
  if (entries.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {entries.map((e) => (
        <span
          key={e.key}
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-surface-container-lowest/80 border border-outline-variant text-[11px] text-on-surface"
        >
          <span className="text-on-surface-variant uppercase tracking-wider text-[9px] font-bold">
            {e.title}
          </span>
          <span className="font-semibold">{e.label}</span>
        </span>
      ))}
    </div>
  );
}

function Card({
  icon,
  title,
  count,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  count?: number;
  children: React.ReactNode;
}) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl overflow-hidden">
      <div className="flex items-center gap-2 px-5 py-3 border-b border-outline-variant/60 bg-surface-container-low/30">
        <div className="w-7 h-7 rounded-lg bg-secondary/10 text-secondary flex items-center justify-center">
          {icon}
        </div>
        <h4 className="text-sm font-bold text-on-surface">{title}</h4>
        {typeof count === 'number' && (
          <span className="ml-auto text-[11px] font-semibold text-on-surface-variant bg-surface-container px-1.5 py-0.5 rounded">
            {count}
          </span>
        )}
      </div>
      <div className="p-5">{children}</div>
    </div>
  );
}

// Renders a row of up-to-two cards. Filters falsy children so that when only
// one side of a pair is present, it expands full-width instead of leaving an
// empty grid column.
function CardRow({ children }: { children: React.ReactNode }) {
  const arr = Children.toArray(children).filter(Boolean);
  if (arr.length === 0) return null;
  if (arr.length === 1) return <>{arr[0]}</>;
  return <div className="grid lg:grid-cols-2 gap-4">{arr}</div>;
}

const DIMENSION_LABELS: Record<string, string> = {
  // Behavioral
  starStructure: 'Cấu trúc STAR',
  relevance: 'Liên quan',
  specificity: 'Cụ thể',
  impactResult: 'Kết quả/Tác động',
  selfAwareness: 'Tự nhận thức',
  // Conceptual
  accuracy: 'Độ chính xác',
  depth: 'Độ sâu',
  practicalApplication: 'Ứng dụng thực tế',
  clarity: 'Rõ ràng',
  // Live coding
  timeComplexity: 'Độ phức tạp thời gian',
  spaceComplexity: 'Độ phức tạp bộ nhớ',
  codeQuality: 'Chất lượng code',
  problemSolving: 'Giải quyết vấn đề',
};

const SEVERITY_LABEL: Record<string, string> = {
  high: 'Cao',
  medium: 'Vừa',
  med: 'Vừa',
  low: 'Thấp',
};

function EvaluationBreakdown({ detail }: { detail: EvaluationDetail | null }) {
  if (!detail) return null;
  switch (detail.kind) {
    case 'BEHAVIORAL':
      return <BehavioralBreakdown detail={detail} />;
    case 'CORE_CONCEPTUAL':
      return <ConceptualBreakdown detail={detail} />;
    case 'LIVE_CODING':
      return <LiveCodingBreakdown detail={detail} />;
  }
}

function ScoreBars({ scores }: { scores: Record<string, ScoreEntry | null> | null }) {
  if (!scores) return null;
  const entries = Object.entries(scores).filter(
    ([, v]) => v && typeof v.score === 'number',
  ) as [string, ScoreEntry & { score: number }][];
  if (entries.length === 0) return null;
  return (
    <ul className="space-y-3">
      {entries.map(([key, entry]) => {
        const tone = scoreTone(entry.score / 10);
        return (
          <li key={key} className="space-y-1.5">
            <div className="flex items-center gap-3">
              <span className="text-[11px] font-bold text-on-surface w-40 shrink-0">
                {DIMENSION_LABELS[key] ?? key}
              </span>
              <div className="flex-1 h-2 rounded-full bg-surface-container overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all ${tone.bar}`}
                  style={{ width: `${Math.max(0, Math.min(100, entry.score))}%` }}
                />
              </div>
              <span
                className={`text-xs font-extrabold w-10 text-right tabular-nums ${tone.text}`}
              >
                {entry.score}
              </span>
            </div>
            {entry.note && (
              <p className="text-[11px] text-on-surface-variant leading-relaxed pl-[10.5rem]">
                {entry.note}
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function FeedbackList({
  items,
  tone,
}: {
  items: string[] | null | undefined;
  tone: 'positive' | 'negative' | 'neutral';
}) {
  if (!items || items.length === 0) return null;
  const dotClass =
    tone === 'positive'
      ? 'text-emerald-500'
      : tone === 'negative'
        ? 'text-amber-500'
        : 'text-secondary';
  return (
    <ul className="space-y-2.5">
      {items.map((item, i) => (
        <li
          key={i}
          className="flex items-start gap-2.5 text-sm text-on-surface leading-relaxed"
        >
          <CheckCircle2 className={`w-4 h-4 ${dotClass} flex-shrink-0 mt-0.5`} />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}

function Excerpt({ text }: { text: string | null | undefined }) {
  if (!text) return null;
  return (
    <blockquote className="mt-1.5 border-l-2 border-secondary/30 pl-3 text-[12px] italic text-on-surface-variant leading-relaxed">
      {text}
    </blockquote>
  );
}

const STAR_LABELS: Record<string, string> = {
  situation: 'Situation — Bối cảnh',
  task: 'Task — Nhiệm vụ',
  action: 'Action — Hành động',
  result: 'Result — Kết quả',
};

const QUALITY_LABEL: Record<string, string> = {
  excellent: 'Xuất sắc',
  good: 'Tốt',
  acceptable: 'Đạt',
  weak: 'Yếu',
  missing: 'Thiếu',
};

const QUALITY_TONE: Record<string, string> = {
  excellent: 'bg-emerald-100 text-emerald-700 border-emerald-200',
  good: 'bg-emerald-50 text-emerald-700 border-emerald-100',
  acceptable: 'bg-amber-50 text-amber-700 border-amber-100',
  weak: 'bg-amber-100 text-amber-700 border-amber-200',
  missing: 'bg-red-100 text-red-700 border-red-200',
};

function FeedbackPair({
  strengths,
  improvements,
}: {
  strengths: string[] | null | undefined;
  improvements: string[] | null | undefined;
}) {
  return (
    <CardRow>
      {strengths && strengths.length > 0 && (
        <Card
          icon={<Sparkles className="w-4 h-4" />}
          title="Điểm mạnh"
          count={strengths.length}
        >
          <FeedbackList items={strengths} tone="positive" />
        </Card>
      )}
      {improvements && improvements.length > 0 && (
        <Card
          icon={<Lightbulb className="w-4 h-4" />}
          title="Nên cải thiện"
          count={improvements.length}
        >
          <FeedbackList items={improvements} tone="negative" />
        </Card>
      )}
    </CardRow>
  );
}

function BehavioralBreakdown({ detail }: { detail: BehavioralEvaluationDetail }) {
  const star = detail.starBreakdown;
  const starEntries: Array<[
    'situation' | 'task' | 'action' | 'result',
    StarComponentItem | null,
  ]> = star
    ? [
        ['situation', star.situation],
        ['task', star.task],
        ['action', star.action],
        ['result', star.result],
      ]
    : [];
  const hasStar = starEntries.some(([, c]) => !!c);
  const hasScores = !!detail.scores;
  const hasSignals = detail.signalCoverage.length > 0;
  const hasRedFlags = detail.redFlags.length > 0;

  return (
    <>
      <CardRow>
        {hasScores && detail.scores && (
          <Card icon={<Layers className="w-4 h-4" />} title="Điểm theo tiêu chí">
            <ScoreBars scores={detail.scores} />
          </Card>
        )}
        {hasStar && (
          <Card icon={<Award className="w-4 h-4" />} title="Phân tích STAR">
              <div className="space-y-3">
                {starEntries.map(([key, comp]) => {
                  if (!comp) return null;
                  const qualityKey = comp.quality?.toLowerCase() ?? '';
                  const toneClass =
                    QUALITY_TONE[qualityKey] ??
                    'bg-surface-container text-on-surface-variant border-outline-variant/60';
                  return (
                    <div
                      key={key}
                      className="border border-outline-variant/60 rounded-xl p-3 bg-surface-container-low/30"
                    >
                      <div className="flex items-center justify-between gap-2 mb-1.5">
                        <span className="text-[12px] font-bold text-on-surface">
                          {STAR_LABELS[key]}
                        </span>
                        {comp.quality && (
                          <span
                            className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded border ${toneClass}`}
                          >
                            {QUALITY_LABEL[qualityKey] ?? comp.quality}
                          </span>
                        )}
                      </div>
                      {!comp.detected && (
                        <p className="text-[11px] text-on-surface-variant italic">
                          Không có trong câu trả lời.
                        </p>
                      )}
                      <Excerpt text={comp.excerpt} />
                    </div>
                  );
                })}
              </div>
            </Card>
          )}
      </CardRow>
      <CardRow>
          {hasSignals && (
            <Card
              icon={<Zap className="w-4 h-4" />}
              title="Tín hiệu mong đợi"
              count={detail.signalCoverage.length}
            >
              <ul className="space-y-3">
                {detail.signalCoverage.map((s) => (
                  <li key={s.signalName} className="text-xs text-on-surface">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span
                        className={
                          s.detected
                            ? 'inline-block w-2 h-2 rounded-full bg-emerald-500'
                            : 'inline-block w-2 h-2 rounded-full bg-on-surface-variant/40'
                        }
                      />
                      <span className="font-semibold text-sm">
                        {s.signalName.replace(/_/g, ' ')}
                      </span>
                      <span
                        className={
                          s.detected
                            ? 'text-[11px] font-semibold uppercase tracking-wider px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700'
                            : 'text-[11px] font-semibold uppercase tracking-wider px-1.5 py-0.5 rounded bg-surface-container text-on-surface-variant'
                        }
                      >
                        {s.detected ? 'có thể hiện' : 'chưa thể hiện'}
                      </span>
                    </div>
                    <Excerpt text={s.evidence} />
                  </li>
                ))}
              </ul>
            </Card>
          )}
          {hasRedFlags && (
            <Card
              icon={<AlertTriangle className="w-4 h-4" />}
              title="Điểm cần lưu ý"
              count={detail.redFlags.length}
            >
              <ul className="space-y-3">
                {detail.redFlags.map((f, i) => (
                  <li
                    key={`${f.type}-${i}`}
                    className="border border-red-200 bg-red-50/40 rounded-lg p-3"
                  >
                    <div className="flex items-center gap-2 flex-wrap mb-1">
                      <span className="font-semibold text-sm text-on-surface">
                        {f.type.replace(/_/g, ' ')}
                      </span>
                      <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded bg-red-100 text-red-700">
                        {SEVERITY_LABEL[f.severity?.toLowerCase()] ?? f.severity}
                      </span>
                    </div>
                    {f.detail && (
                      <p className="text-[12px] text-on-surface leading-relaxed">
                        {f.detail}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </Card>
          )}
      </CardRow>
      <FeedbackPair
        strengths={detail.feedback?.strengths}
        improvements={detail.feedback?.improvements}
      />
      {detail.feedback?.sampleStrongerAnswerStructure && (
        <Card
          icon={<Lightbulb className="w-4 h-4" />}
          title="Gợi ý cấu trúc câu trả lời mạnh hơn"
        >
          <p className="text-sm text-on-surface leading-relaxed whitespace-pre-line">
            {detail.feedback.sampleStrongerAnswerStructure}
          </p>
        </Card>
      )}
    </>
  );
}

function ConceptualBreakdown({ detail }: { detail: ConceptualEvaluationDetail }) {
  const lc = detail.levelCalibration;
  const hasScores = !!detail.scores;
  const hasLc = !!lc && !!(lc.expectedLevel || lc.actualDemonstratedLevel || lc.gap);
  const hasConcepts = detail.conceptCoverage.length > 0;
  const hasMisc = detail.misconceptions.length > 0;

  return (
    <>
      <CardRow>
          {hasScores && detail.scores && (
            <Card icon={<Layers className="w-4 h-4" />} title="Điểm theo tiêu chí">
              <ScoreBars scores={detail.scores} />
            </Card>
          )}
          {hasLc && lc && (
            <Card icon={<Target className="w-4 h-4" />} title="Mức độ thể hiện">
              <div className="grid grid-cols-2 gap-3 mb-2">
                <div className="border border-outline-variant/60 rounded-lg p-3 bg-surface-container-low/30">
                  <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1">
                    Mong đợi
                  </p>
                  <p className="text-sm font-bold text-on-surface">
                    {lc.expectedLevel ?? '—'}
                  </p>
                </div>
                <div className="border border-outline-variant/60 rounded-lg p-3 bg-surface-container-low/30">
                  <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1">
                    Thực tế
                  </p>
                  <p className="text-sm font-bold text-on-surface">
                    {lc.actualDemonstratedLevel ?? '—'}
                  </p>
                </div>
              </div>
              {lc.gap && (
                <p className="text-xs text-on-surface-variant leading-relaxed">
                  {lc.gap}
                </p>
              )}
            </Card>
          )}
      </CardRow>
      <CardRow>
          {hasConcepts && (
            <Card
              icon={<BookOpen className="w-4 h-4" />}
              title="Khái niệm cốt lõi"
              count={detail.conceptCoverage.length}
            >
              <ul className="space-y-3">
                {detail.conceptCoverage.map((c) => {
                  const status = !c.mentioned
                    ? { color: 'bg-on-surface-variant/40', text: 'không nhắc tới' }
                    : c.correct === false
                      ? { color: 'bg-red-500', text: 'nói sai' }
                      : c.correct === true
                        ? { color: 'bg-emerald-500', text: 'đúng' }
                        : { color: 'bg-amber-500', text: 'có nhắc, chưa rõ đúng/sai' };
                  return (
                    <li key={c.conceptName} className="text-xs text-on-surface">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span
                          className={`inline-block w-2 h-2 rounded-full ${status.color}`}
                        />
                        <span className="font-semibold text-sm">
                          {c.conceptName.replace(/_/g, ' ')}
                        </span>
                        <span className="text-on-surface-variant text-[11px]">
                          — {status.text}
                        </span>
                      </div>
                      <Excerpt text={c.candidateStatement} />
                      {c.correction && (
                        <p className="mt-1.5 text-[12px] text-emerald-700 leading-relaxed bg-emerald-50 border border-emerald-200 rounded-md px-2.5 py-2">
                          <span className="font-bold">Đúng là:</span> {c.correction}
                        </p>
                      )}
                    </li>
                  );
                })}
              </ul>
            </Card>
          )}
          {hasMisc && (
            <Card
              icon={<AlertTriangle className="w-4 h-4" />}
              title="Hiểu nhầm cần sửa"
              count={detail.misconceptions.length}
            >
              <ul className="space-y-3">
                {detail.misconceptions.map((m, i) => (
                  <li key={i} className="text-xs text-on-surface">
                    <p className="text-on-surface leading-relaxed text-sm">
                      <span className="font-semibold text-red-700">Sai:</span>{' '}
                      {m.claim}
                    </p>
                    {m.correction && (
                      <p className="mt-1 text-[12px] text-emerald-700 leading-relaxed bg-emerald-50 border border-emerald-200 rounded-md px-2.5 py-2">
                        <span className="font-bold">Đúng là:</span> {m.correction}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </Card>
          )}
      </CardRow>
      <FeedbackPair
        strengths={detail.feedback?.strengths}
        improvements={detail.feedback?.improvements}
      />
      {detail.feedback?.keyPointsToStudy &&
        detail.feedback.keyPointsToStudy.length > 0 && (
          <Card
            icon={<BookOpen className="w-4 h-4" />}
            title="Nên học thêm"
            count={detail.feedback.keyPointsToStudy.length}
          >
            <FeedbackList
              items={detail.feedback.keyPointsToStudy}
              tone="neutral"
            />
          </Card>
        )}
    </>
  );
}

function LiveCodingBreakdown({ detail }: { detail: LiveCodingEvaluationDetail }) {
  const detected = detail.detectedComplexity;
  const optimal = detail.optimalComplexity;
  const showComplexity = !!(detected || optimal);
  const hasScores = !!detail.scores;
  return (
    <>
      <CardRow>
          {hasScores && detail.scores && (
            <Card icon={<Layers className="w-4 h-4" />} title="Điểm theo tiêu chí">
              <ScoreBars scores={detail.scores} />
            </Card>
          )}
          {showComplexity && (
            <Card icon={<Zap className="w-4 h-4" />} title="Độ phức tạp">
          <div className="grid grid-cols-3 gap-2 text-xs items-center">
            <div />
            <div className="text-[10px] font-bold uppercase tracking-wider text-on-surface-variant text-center">
              Code của bạn
            </div>
            <div className="text-[10px] font-bold uppercase tracking-wider text-emerald-700 text-center">
              Tối ưu
            </div>
            <div className="font-semibold text-on-surface-variant">Thời gian</div>
            <div className="text-center font-mono text-sm py-2 rounded-md bg-surface-container-low/60 text-on-surface">
              {detected?.time ?? '—'}
            </div>
            <div className="text-center font-mono text-sm py-2 rounded-md bg-emerald-50 text-emerald-700">
              {optimal?.time ?? '—'}
            </div>
            <div className="font-semibold text-on-surface-variant">Bộ nhớ</div>
            <div className="text-center font-mono text-sm py-2 rounded-md bg-surface-container-low/60 text-on-surface">
              {detected?.space ?? '—'}
            </div>
            <div className="text-center font-mono text-sm py-2 rounded-md bg-emerald-50 text-emerald-700">
              {optimal?.space ?? '—'}
            </div>
          </div>
          {typeof detail.isOptimal === 'boolean' && (
            <p
              className={`mt-3 text-xs font-semibold flex items-center gap-1.5 ${
                detail.isOptimal ? 'text-emerald-700' : 'text-amber-700'
              }`}
            >
              {detail.isOptimal ? (
                <CheckCircle2 className="w-3.5 h-3.5" />
              ) : (
                <AlertTriangle className="w-3.5 h-3.5" />
              )}
              {detail.isOptimal
                ? 'Đã đạt độ phức tạp tối ưu.'
                : 'Chưa đạt độ phức tạp tối ưu — xem gợi ý bên dưới.'}
            </p>
          )}
            </Card>
          )}
      </CardRow>
      {detail.codeIssues.length > 0 && (
        <Card
          icon={<AlertTriangle className="w-4 h-4" />}
          title="Vấn đề trong code"
          count={detail.codeIssues.length}
        >
          <ul className="space-y-3">
            {detail.codeIssues.map((i, idx) => (
              <li
                key={`${i.type}-${idx}`}
                className="border border-outline-variant/60 rounded-lg p-3 bg-surface-container-low/30"
              >
                <div className="flex items-center gap-2 flex-wrap mb-1">
                  <span className="font-semibold text-sm text-on-surface">
                    {i.type.replace(/_/g, ' ')}
                  </span>
                  {typeof i.line === 'number' && (
                    <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-surface-container text-on-surface-variant">
                      dòng {i.line}
                    </span>
                  )}
                </div>
                {i.detail && (
                  <p className="text-[12px] text-on-surface-variant leading-relaxed">
                    {i.detail}
                  </p>
                )}
              </li>
            ))}
          </ul>
        </Card>
      )}
      <FeedbackPair
        strengths={detail.feedback?.strengths}
        improvements={detail.feedback?.improvements}
      />
      {detail.feedback?.optimizationHint && (
        <Card icon={<Lightbulb className="w-4 h-4" />} title="Gợi ý tối ưu">
          <p className="text-sm text-on-surface leading-relaxed">
            {detail.feedback.optimizationHint}
          </p>
        </Card>
      )}
      {detail.feedback?.sampleOptimalSolution && (
        <Card
          icon={<Code2 className="w-4 h-4" />}
          title="Lời giải mẫu tối ưu"
        >
          <pre className="text-[12px] leading-relaxed bg-on-surface text-inverse-on-surface rounded-lg p-4 overflow-x-auto font-mono">
            {detail.feedback.sampleOptimalSolution}
          </pre>
        </Card>
      )}
    </>
  );
}

function AnswerVideo({
  mediaUrl,
  onReload,
}: {
  mediaUrl: string;
  onReload: () => Promise<SessionView | null>;
}) {
  // Drop the blob-URL fetch: mediaUrl is now a presigned MinIO URL behind the
  // nginx /minio/ proxy, so the browser can play it directly. SigV4 lives in
  // the query string — no Authorization header to attach.
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [errored, setErrored] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [reloadFailed, setReloadFailed] = useState(false);
  // While the demuxer is computing the real duration, the native scrubber
  // would otherwise flash a bogus value (e.g. 277777:46:40 from seeking to
  // 1e9). Hide controls behind an overlay until durationchange fires finite.
  const [durationReady, setDurationReady] = useState(false);

  // MediaRecorder WebM blobs ship without a valid `duration` header, so the
  // native scrubber reads `Infinity` and jumps around when you try to seek.
  // Trick the demuxer into computing the real duration: seek past the end,
  // wait for `durationchange` to fire with a finite value, then snap back
  // to 0. After that the scrub bar tracks playback correctly.
  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    setDurationReady(false);
    let phase: 'idle' | 'seeking' | 'done' = 'idle';

    const onLoadedMetadata = () => {
      if (phase !== 'idle') return;
      if (!Number.isFinite(v.duration)) {
        phase = 'seeking';
        try {
          v.currentTime = 1e9;
        } catch {
          // Some browsers throw on out-of-range seeks; fall through and the
          // duration will simply stay Infinity (better than a crash).
          phase = 'done';
        }
      } else {
        phase = 'done';
        setDurationReady(true);
      }
    };
    const onDurationChange = () => {
      if (!Number.isFinite(v.duration) || v.duration <= 0) return;
      if (phase === 'seeking') {
        phase = 'done';
        v.currentTime = 0;
      }
      setDurationReady(true);
    };

    v.addEventListener('loadedmetadata', onLoadedMetadata);
    v.addEventListener('durationchange', onDurationChange);
    return () => {
      v.removeEventListener('loadedmetadata', onLoadedMetadata);
      v.removeEventListener('durationchange', onDurationChange);
    };
  }, [mediaUrl]);

  const retry = useCallback(async () => {
    setReloadFailed(false);
    setReloading(true);
    try {
      // Re-fetch the session — the BE issues a fresh presigned URL on each
      // call, so an expired signature is healed by simply reloading.
      await onReload();
      setErrored(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) return;
      setReloadFailed(true);
    } finally {
      setReloading(false);
    }
  }, [onReload]);

  if (errored) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-xl p-4 flex items-start gap-2.5">
        <AlertTriangle className="w-4 h-4 text-red-600 mt-0.5 shrink-0" />
        <div className="flex-1">
          <p className="text-xs text-red-700 mb-2">
            {reloadFailed
              ? 'Không tải lại được video. Vui lòng thử lại sau.'
              : 'Không tải được video. URL có thể đã hết hạn.'}
          </p>
          <button
            type="button"
            onClick={retry}
            disabled={reloading}
            className="text-xs text-red-700 underline font-semibold disabled:opacity-50"
          >
            {reloading ? 'Đang thử lại…' : 'Thử lại'}
          </button>
        </div>
      </div>
    );
  }
  return (
    <div className="relative">
      <video
        ref={videoRef}
        // key forces a fresh element when the URL changes after onReload,
        // otherwise <video> would stick with the previous (expired) src.
        key={mediaUrl}
        src={mediaUrl}
        controls={durationReady}
        playsInline
        preload="metadata"
        onError={() => setErrored(true)}
        className="w-full max-h-[60vh] rounded-xl bg-black"
      />
      {!durationReady && (
        <div className="absolute inset-0 flex items-center justify-center bg-black/55 rounded-xl pointer-events-none">
          <div className="flex items-center gap-2 text-white text-xs font-semibold">
            <Loader2 className="w-4 h-4 animate-spin" />
            Đang chuẩn bị video…
          </div>
        </div>
      )}
    </div>
  );
}

