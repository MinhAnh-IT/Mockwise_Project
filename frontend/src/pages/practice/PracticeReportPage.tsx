import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Award,
  CalendarClock,
  CheckCircle2,
  Lightbulb,
  Loader2,
  Quote,
  Sparkles,
  Target,
  ThumbsDown,
  ThumbsUp,
  Timer,
  TrendingUp,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getSession } from '@/api/interviews';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import { findPracticeOption } from '@/data/practice';
import type {
  OverallReviewView,
  SessionView,
  TopicProgress,
} from '@/types/interview';

const POLL_INTERVAL_MS = 3000;
const MAX_POLL_ATTEMPTS = 60; // ~3 phút

type OverallReview = OverallReviewView;
type TopicSummaryEntry = NonNullable<OverallReviewView['perTopicSummary']>[number];

const TOPIC_STATUS_LABEL: Record<string, string> = {
  STRONG: 'Tốt',
  ADEQUATE: 'Đạt',
  PROBING: 'Đang đánh giá',
  PARTIAL: 'Chưa đủ',
  WEAK: 'Yếu',
  UNKNOWN: 'Bỏ qua',
  NOT_TESTED: 'Chưa hỏi',
};

const TOPIC_STATUS_TONE: Record<string, string> = {
  STRONG: 'bg-emerald-100 text-emerald-700 border-emerald-200',
  ADEQUATE: 'bg-emerald-50 text-emerald-700 border-emerald-100',
  PROBING: 'bg-amber-50 text-amber-700 border-amber-100',
  PARTIAL: 'bg-amber-100 text-amber-700 border-amber-200',
  WEAK: 'bg-red-100 text-red-700 border-red-200',
  UNKNOWN: 'bg-surface-container text-on-surface-variant border-outline-variant/60',
  NOT_TESTED: 'bg-surface-container text-on-surface-variant border-outline-variant/60',
};

const TOPIC_STATUS_BAR: Record<string, string> = {
  STRONG: 'bg-emerald-500',
  ADEQUATE: 'bg-emerald-400',
  PROBING: 'bg-amber-400',
  PARTIAL: 'bg-amber-500',
  WEAK: 'bg-red-500',
  UNKNOWN: 'bg-on-surface-variant/30',
  NOT_TESTED: 'bg-on-surface-variant/30',
};

const TYPE_LABEL: Record<string, string> = {
  BEHAVIORAL: 'Hành vi',
  CORE: 'Chuyên môn',
  CODING: 'Lập trình',
};

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/**
 * Overall report for a finished session. Renders score / topics /
 * strengths / weaknesses / recommendations. The per-question detail lives
 * on its own sibling route ({@code /questions}) — link out via the CTA at
 * the bottom.
 */
export default function PracticeReportPage() {
  const { type, sid } = useParams<{ type: string; sid: string }>();
  const navigate = useNavigate();
  const option = type ? findPracticeOption(type) : undefined;

  const [session, setSession] = useState<SessionView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const attemptsRef = useRef(0);

  useEffect(() => {
    if (!sid) return;
    let cancelled = false;
    let timer: number | null = null;

    const tick = async () => {
      try {
        const s = await getSession(sid);
        if (cancelled) return;
        setSession(s);
        // Terminal states — stop polling. SCORED has a report; CANCELLED never
        // will (session ended with no answers to grade, so the backend skips
        // the overall review). Polling on past CANCELLED just spun the
        // "đang tổng hợp" placeholder until the attempt cap, then surfaced a
        // misleading "scoring is slow" error.
        if (s.status === 'SCORED' || s.status === 'CANCELLED') return;
        attemptsRef.current += 1;
        if (attemptsRef.current >= MAX_POLL_ATTEMPTS) {
          setError('Hệ thống chấm điểm đang chậm hơn dự kiến. Bạn có thể quay lại trang này sau.');
          return;
        }
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) return;
        const msg = err instanceof Error ? err.message : 'Không tải được báo cáo.';
        setError(msg);
        return;
      }
      if (!cancelled) timer = window.setTimeout(tick, POLL_INTERVAL_MS);
    };

    tick();
    return () => {
      cancelled = true;
      if (timer !== null) window.clearTimeout(timer);
    };
  }, [sid]);

  if (!option || !sid) {
    navigate('/practice', { replace: true });
    return null;
  }

  const review: OverallReview | null = session?.overallReview ?? null;
  const isScored = session?.status === 'SCORED';
  const questionsHref = `/practice/${type}/session/${sid}/questions`;
  const hasQuestions = !!session && session.questions.length > 0;

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />

      <main className="flex-1 px-4 md:px-8 pt-24 pb-16">
        <div className="max-w-6xl mx-auto">
          <div className="flex items-center justify-between gap-3 mb-5 flex-wrap">
            <Link
              to="/history"
              className="inline-flex items-center gap-1 text-sm font-medium text-on-surface-variant hover:text-on-surface transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              Về lịch sử phỏng vấn
            </Link>
            {isScored && hasQuestions && (
              <Link
                to={questionsHref}
                className="hidden md:inline-flex items-center gap-1.5 text-sm font-semibold text-secondary hover:underline"
              >
                Chi tiết từng câu
                <ArrowRight className="w-4 h-4" />
              </Link>
            )}
          </div>

          <PageTitle option={option} session={session} />

          {!isScored && (
            <ScoringPlaceholder status={session?.status} error={error} />
          )}

          {isScored && session && (
            <>
              <HeroScoreCard review={review} session={session} />

              <MetaStrip session={session} />

              {session.topicProgress.length > 0 && (
                <TopicBreakdown
                  topics={session.topicProgress}
                  perTopicComments={review?.perTopicSummary ?? []}
                />
              )}

              <div className="grid md:grid-cols-2 gap-4 mb-5">
                {review?.strengths && review.strengths.length > 0 && (
                  <BulletSection
                    title="Điểm mạnh"
                    tone="positive"
                    icon={<ThumbsUp className="w-4 h-4" />}
                    items={review.strengths}
                  />
                )}
                {review?.weaknesses && review.weaknesses.length > 0 && (
                  <BulletSection
                    title="Cần cải thiện"
                    tone="negative"
                    icon={<ThumbsDown className="w-4 h-4" />}
                    items={review.weaknesses}
                  />
                )}
              </div>

              {review?.recommendations && review.recommendations.length > 0 && (
                <RecommendationsCard items={review.recommendations} />
              )}

              {hasQuestions && <ContinueCta href={questionsHref} />}
            </>
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
        Tổng quan phỏng vấn
      </p>
      <h1 className="text-2xl md:text-3xl font-extrabold text-on-surface tracking-tight">
        {option.title}
      </h1>
      {session && (
        <p className="mt-1 text-sm text-on-surface-variant">
          {session.targetRole} · {session.level} ·{' '}
          {TYPE_LABEL[session.interviewType] ?? session.interviewType}
        </p>
      )}
    </div>
  );
}

function ScoringPlaceholder({
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
          Có lỗi tải báo cáo
        </p>
        <p className="text-xs text-red-600 leading-relaxed">{error}</p>
      </div>
    );
  }
  // CANCELLED is terminal with no report: the session was ended before any
  // answer was submitted, so there is nothing to grade. Show a clear final
  // message instead of an endless "đang tổng hợp" spinner.
  if (status === 'CANCELLED') {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-3xl p-10 md:p-14 text-center">
        <div className="w-16 h-16 mx-auto mb-5 rounded-2xl bg-on-surface-variant/10 text-on-surface-variant flex items-center justify-center">
          <CalendarClock className="w-8 h-8" />
        </div>
        <h3 className="text-lg font-bold text-on-surface mb-2">
          Phiên phỏng vấn đã kết thúc
        </h3>
        <p className="text-sm text-on-surface-variant max-w-md mx-auto leading-relaxed">
          Bạn đã kết thúc phiên này trước khi trả lời câu hỏi nào, nên không có
          báo cáo đánh giá. Hãy bắt đầu một phiên mới và hoàn thành ít nhất một
          câu để nhận kết quả.
        </p>
        <Link
          to="/practice"
          className="inline-flex items-center gap-1.5 mt-5 px-5 py-2.5 rounded-xl bg-secondary text-on-secondary text-sm font-semibold hover:opacity-95 transition-opacity"
        >
          Bắt đầu phiên mới
          <ArrowRight className="w-4 h-4" />
        </Link>
      </div>
    );
  }
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-3xl p-10 md:p-14 text-center relative overflow-hidden">
      <div className="absolute -right-12 -top-12 w-56 h-56 rounded-full bg-secondary/5 blur-3xl pointer-events-none" />
      <div className="absolute -left-12 -bottom-12 w-56 h-56 rounded-full bg-secondary/5 blur-3xl pointer-events-none" />

      <div className="relative">
        <div className="w-16 h-16 mx-auto mb-5 rounded-2xl bg-secondary/10 text-secondary flex items-center justify-center">
          <Loader2 className="w-8 h-8 animate-spin" />
        </div>
        <h3 className="text-lg font-bold text-on-surface mb-2">
          {status === 'COMPLETED'
            ? 'Đang tổng hợp báo cáo…'
            : 'Đang chấm các câu trả lời…'}
        </h3>
        <p className="text-sm text-on-surface-variant max-w-md mx-auto leading-relaxed">
          Hệ thống đang phân tích câu trả lời của bạn. Việc này thường mất 1–3
          phút tuỳ độ dài video. Trang sẽ tự cập nhật khi xong.
        </p>
      </div>
    </div>
  );
}

function HeroScoreCard({
  review,
  session,
}: {
  review: OverallReview | null;
  session: SessionView;
}) {
  const score = review?.overallScore ?? session.finalScore;
  const grade = review?.grade ?? null;
  const hireSignal = review?.hireSignal ?? null;

  return (
    <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-secondary-fixed via-surface-container-lowest to-surface-container-low border border-outline-variant/60 p-6 md:p-8 mb-5">
      <div className="absolute -right-20 -top-20 w-72 h-72 rounded-full bg-secondary/10 blur-3xl pointer-events-none" />
      <div className="absolute -left-20 -bottom-20 w-72 h-72 rounded-full bg-on-secondary-fixed/5 blur-3xl pointer-events-none" />

      <div className="relative flex flex-col md:flex-row md:items-center gap-6 md:gap-10">
        <ScoreRingLarge score={score} />

        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2 mb-3">
            {grade && (
              <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-secondary text-on-secondary text-xs font-bold tracking-wider">
                <Award className="w-3.5 h-3.5" />
                Grade {grade}
              </span>
            )}
            {hireSignal && (
              <span className="inline-flex items-center px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant text-[10px] font-bold uppercase tracking-widest text-on-surface">
                {hireSignal.replace(/_/g, ' ')}
              </span>
            )}
          </div>
          {review?.summary ? (
            <div className="relative">
              <Quote className="absolute -left-1 -top-2 w-5 h-5 text-secondary/30" />
              <p className="pl-5 text-sm md:text-base text-on-surface leading-relaxed">
                {review.summary}
              </p>
            </div>
          ) : (
            <p className="text-sm text-on-surface-variant italic">
              Hệ thống chưa có đánh giá tổng quan cho phiên này.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

function ScoreRingLarge({ score }: { score: number | null }) {
  const size = 144;
  const stroke = 12;
  const radius = (size - stroke) / 2;
  const circ = 2 * Math.PI * radius;
  const pct =
    typeof score === 'number' ? Math.max(0, Math.min(1, score / 10)) : 0;
  const tone = scoreTone(score);

  return (
    <div
      className="relative shrink-0 mx-auto md:mx-0"
      style={{ width: size, height: size }}
    >
      <svg viewBox={`0 0 ${size} ${size}`} className="w-full h-full -rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="currentColor"
          className="text-surface-container"
          strokeWidth={stroke}
        />
        {typeof score === 'number' && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke="currentColor"
            className={tone.ring}
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={circ}
            strokeDashoffset={circ * (1 - pct)}
          />
        )}
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        {typeof score === 'number' ? (
          <>
            <span
              className={`text-4xl font-extrabold leading-none ${tone.text}`}
            >
              {score.toFixed(1)}
            </span>
            <span className="text-xs text-on-surface-variant font-semibold mt-1">
              / 10
            </span>
          </>
        ) : (
          <span className="text-xs text-on-surface-variant font-semibold text-center px-2">
            chưa có điểm
          </span>
        )}
      </div>
    </div>
  );
}

function scoreTone(score: number | null) {
  if (typeof score !== 'number') {
    return { ring: 'text-on-surface-variant/30', text: 'text-on-surface-variant' };
  }
  if (score >= 8) return { ring: 'text-emerald-500', text: 'text-emerald-700' };
  if (score >= 6.5) return { ring: 'text-secondary', text: 'text-secondary' };
  if (score >= 5) return { ring: 'text-amber-500', text: 'text-amber-700' };
  return { ring: 'text-red-500', text: 'text-red-700' };
}

function MetaStrip({ session }: { session: SessionView }) {
  const dateText = DATE_FMT.format(new Date(session.startedAt));
  const duration = durationMinutes(session.startedAt, session.finishedAt);
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
      <MetaTile
        icon={<CalendarClock className="w-4 h-4" />}
        label="Thời điểm"
        value={dateText}
      />
      <MetaTile
        icon={<Target className="w-4 h-4" />}
        label="Vị trí"
        value={`${session.targetRole} · ${session.level}`}
      />
      <MetaTile
        icon={<Sparkles className="w-4 h-4" />}
        label="Số câu hỏi"
        value={`${session.questionCount} câu`}
      />
      <MetaTile
        icon={<Timer className="w-4 h-4" />}
        label="Thời lượng"
        value={
          duration !== null
            ? `${duration} phút`
            : `${session.timeBudgetMinutes} phút (kế hoạch)`
        }
      />
    </div>
  );
}

function MetaTile({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-3 md:p-4 flex items-center gap-3 min-w-0">
      <div className="w-9 h-9 rounded-lg bg-secondary/10 text-secondary flex items-center justify-center shrink-0">
        {icon}
      </div>
      <div className="min-w-0">
        <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
          {label}
        </p>
        <p className="text-sm font-semibold text-on-surface truncate mt-0.5">
          {value}
        </p>
      </div>
    </div>
  );
}

function durationMinutes(start: string, end: string | null) {
  if (!end) return null;
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (!Number.isFinite(ms) || ms <= 0) return null;
  return Math.max(1, Math.round(ms / 60000));
}

function TopicBreakdown({
  topics,
  perTopicComments,
}: {
  topics: TopicProgress[];
  perTopicComments: TopicSummaryEntry[];
}) {
  const commentByKey = new Map(
    perTopicComments
      .filter((t) => t.topicKind && t.topicValue)
      .map((t) => [`${t.topicKind}:${t.topicValue}`, t.comment ?? '']),
  );
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-5 md:p-6 mb-5">
      <div className="flex items-center gap-2 mb-4">
        <div className="w-8 h-8 rounded-lg bg-secondary/10 text-secondary flex items-center justify-center">
          <Target className="w-4 h-4" />
        </div>
        <h3 className="text-sm md:text-base font-bold text-on-surface">
          Đánh giá theo chủ đề
        </h3>
      </div>
      <div className="grid md:grid-cols-2 gap-3">
        {topics.map((t) => {
          const comment = commentByKey.get(`${t.topicKind}:${t.topicValue}`);
          const toneClass =
            TOPIC_STATUS_TONE[t.status] ??
            'bg-surface-container text-on-surface-variant border-outline-variant/60';
          const barClass =
            TOPIC_STATUS_BAR[t.status] ?? 'bg-on-surface-variant/30';
          const scorePct =
            typeof t.lastScore === 'number'
              ? Math.max(0, Math.min(100, t.lastScore * 10))
              : null;
          return (
            <div
              key={`${t.topicKind}:${t.topicValue}`}
              className="border border-outline-variant/60 rounded-xl p-4 bg-surface-container-low/40"
            >
              <div className="flex items-start justify-between gap-3 mb-2">
                <p className="text-sm font-semibold text-on-surface flex-1 min-w-0">
                  {t.topicValue.replace(/_/g, ' ')}
                </p>
                <span
                  className={`text-[10px] font-bold uppercase tracking-widest px-2 py-1 rounded-md border shrink-0 ${toneClass}`}
                >
                  {TOPIC_STATUS_LABEL[t.status] ?? t.status}
                </span>
              </div>
              <div className="h-1.5 rounded-full bg-surface-container overflow-hidden mb-2">
                <div
                  className={`h-full rounded-full transition-all ${barClass}`}
                  style={{ width: `${scorePct ?? 0}%` }}
                />
              </div>
              <p className="text-[11px] text-on-surface-variant mb-2">
                {t.questionsAsked} câu
                {t.followUpsUsed > 0 ? ` · ${t.followUpsUsed} câu nối tiếp` : ''}
                {typeof t.lastScore === 'number'
                  ? ` · điểm câu cuối ${t.lastScore.toFixed(1)}`
                  : ''}
              </p>
              {comment && (
                <p className="text-xs text-on-surface leading-relaxed">
                  {comment}
                </p>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function BulletSection({
  title,
  items,
  icon,
  tone,
}: {
  title: string;
  items: string[];
  icon: React.ReactNode;
  tone: 'positive' | 'negative' | 'neutral';
}) {
  const { headerBg, headerText, dot } =
    tone === 'positive'
      ? {
          headerBg: 'bg-emerald-50',
          headerText: 'text-emerald-700',
          dot: 'text-emerald-500',
        }
      : tone === 'negative'
        ? {
            headerBg: 'bg-amber-50',
            headerText: 'text-amber-700',
            dot: 'text-amber-500',
          }
        : {
            headerBg: 'bg-secondary/10',
            headerText: 'text-secondary',
            dot: 'text-secondary',
          };
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl overflow-hidden">
      <div
        className={`flex items-center gap-2 px-5 py-3 ${headerBg} ${headerText}`}
      >
        {icon}
        <h3 className="text-sm font-bold">{title}</h3>
        <span className="ml-auto text-xs font-semibold opacity-70">
          {items.length}
        </span>
      </div>
      <ul className="p-5 space-y-3">
        {items.map((item, i) => (
          <li
            key={i}
            className="flex items-start gap-2.5 text-sm text-on-surface"
          >
            <CheckCircle2 className={`w-4 h-4 ${dot} flex-shrink-0 mt-0.5`} />
            <span className="leading-relaxed">{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function RecommendationsCard({ items }: { items: string[] }) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl overflow-hidden mb-5">
      <div className="flex items-center gap-2 px-5 py-3 bg-secondary/10 text-secondary">
        <Lightbulb className="w-4 h-4" />
        <h3 className="text-sm font-bold">Khuyến nghị tiếp theo</h3>
      </div>
      <ol className="p-5 space-y-3">
        {items.map((item, i) => (
          <li
            key={i}
            className="flex items-start gap-3 text-sm text-on-surface"
          >
            <span className="shrink-0 w-6 h-6 rounded-lg bg-secondary text-on-secondary text-[11px] font-bold flex items-center justify-center mt-0.5 tabular-nums">
              {i + 1}
            </span>
            <span className="leading-relaxed">{item}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}

function ContinueCta({ href }: { href: string }) {
  return (
    <Link
      to={href}
      className="group relative overflow-hidden flex items-center justify-between gap-3 bg-on-surface text-inverse-on-surface rounded-2xl px-6 py-5 font-semibold hover:opacity-95 transition-opacity"
    >
      <div className="flex items-center gap-3">
        <TrendingUp className="w-5 h-5" />
        <span>Xem chi tiết từng câu trả lời</span>
      </div>
      <ArrowRight className="w-5 h-5 transition-transform group-hover:translate-x-1" />
    </Link>
  );
}
