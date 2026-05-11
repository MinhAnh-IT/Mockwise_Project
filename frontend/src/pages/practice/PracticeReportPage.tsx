import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  Loader2,
  ThumbsDown,
  ThumbsUp,
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
  STRONG: 'bg-emerald-100 text-emerald-700',
  ADEQUATE: 'bg-emerald-50 text-emerald-700',
  PROBING: 'bg-amber-50 text-amber-700',
  PARTIAL: 'bg-amber-100 text-amber-700',
  WEAK: 'bg-red-100 text-red-700',
  UNKNOWN: 'bg-surface-container text-on-surface-variant',
  NOT_TESTED: 'bg-surface-container text-on-surface-variant',
};

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
        if (s.status === 'SCORED') return; // stop polling
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

      <main className="flex-1 px-6 pt-24 pb-16">
        <div className="max-w-3xl mx-auto">
          <Link
            to="/practice"
            className="inline-flex items-center gap-1 text-sm font-medium text-on-surface-variant hover:text-on-surface mb-4 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Về trang luyện tập
          </Link>

          <div className="mb-6">
            <p className="text-[10px] font-bold uppercase tracking-widest text-secondary mb-2">
              Tổng quan phỏng vấn
            </p>
            <h1 className="text-2xl md:text-3xl font-bold text-on-surface mb-1">
              {option.title}
            </h1>
            {session && (
              <p className="text-sm text-on-surface-variant">
                {session.targetRole} · {session.level} · {session.questionCount} câu
              </p>
            )}
          </div>

          {!isScored && (
            <ScoringPlaceholder
              status={session?.status}
              error={error}
            />
          )}

          {isScored && review && <OverallSummary review={review} />}

          {isScored && session && session.topicProgress.length > 0 && (
            <TopicBreakdown
              topics={session.topicProgress}
              perTopicComments={review?.perTopicSummary ?? []}
            />
          )}

          {isScored && review?.strengths && review.strengths.length > 0 && (
            <BulletSection
              title="Điểm mạnh"
              tone="positive"
              icon={<ThumbsUp className="w-4 h-4" />}
              items={review.strengths}
            />
          )}

          {isScored && review?.weaknesses && review.weaknesses.length > 0 && (
            <BulletSection
              title="Cần cải thiện"
              tone="negative"
              icon={<ThumbsDown className="w-4 h-4" />}
              items={review.weaknesses}
            />
          )}

          {isScored && review?.recommendations && review.recommendations.length > 0 && (
            <BulletSection
              title="Khuyến nghị tiếp theo"
              tone="neutral"
              icon={<TrendingUp className="w-4 h-4" />}
              items={review.recommendations}
            />
          )}

          {isScored && hasQuestions && (
            <Link
              to={questionsHref}
              className="mt-2 flex items-center justify-between gap-3 bg-secondary text-on-secondary rounded-2xl px-6 py-4 font-semibold hover:opacity-90 transition-opacity"
            >
              <span>Xem chi tiết từng câu trả lời</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          )}
        </div>
      </main>

      <Footer />
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
        <p className="text-sm text-red-700 font-semibold mb-2">Có lỗi tải báo cáo</p>
        <p className="text-xs text-red-600 leading-relaxed">{error}</p>
      </div>
    );
  }
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-10 text-center">
      <Loader2 className="w-8 h-8 mx-auto mb-4 text-secondary animate-spin" />
      <h3 className="text-base font-bold text-on-surface mb-2">
        {status === 'COMPLETED' || status === 'CANCELLED'
          ? 'Đang tổng hợp báo cáo…'
          : 'Đang chấm các câu trả lời…'}
      </h3>
      <p className="text-xs text-on-surface-variant max-w-md mx-auto leading-relaxed">
        Hệ thống đang phân tích câu trả lời của bạn. Việc này thường mất 1–3 phút tuỳ độ dài video.
        Trang sẽ tự cập nhật khi xong.
      </p>
    </div>
  );
}

function OverallSummary({ review }: { review: OverallReview }) {
  const score = review.overallScore;
  const grade = review.grade;
  return (
    <div className="bg-gradient-to-br from-secondary/10 via-surface-container-lowest to-surface-container-low border border-outline-variant rounded-3xl p-8 mb-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-6">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-2">
            Tổng kết
          </p>
          <div className="flex items-baseline gap-3 mb-2">
            {typeof score === 'number' && (
              <span className="text-4xl md:text-5xl font-extrabold text-on-surface">
                {score.toFixed(1)}
                <span className="text-base text-on-surface-variant font-medium">/10</span>
              </span>
            )}
            {grade && (
              <span className="px-3 py-1 rounded-full bg-secondary text-on-secondary text-sm font-bold">
                Grade {grade}
              </span>
            )}
          </div>
        </div>
      </div>
      {review.summary && (
        <p className="mt-6 text-sm text-on-surface leading-relaxed">{review.summary}</p>
      )}
    </div>
  );
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
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6 mb-6">
      <h3 className="text-sm font-bold text-on-surface mb-4">Theo chủ đề</h3>
      <div className="space-y-3">
        {topics.map((t) => {
          const comment = commentByKey.get(`${t.topicKind}:${t.topicValue}`);
          return (
            <div
              key={`${t.topicKind}:${t.topicValue}`}
              className="border border-outline-variant/60 rounded-xl p-4"
            >
              <div className="flex items-center justify-between gap-3 mb-1.5">
                <p className="text-sm font-semibold text-on-surface">
                  {t.topicValue.replace(/_/g, ' ')}
                </p>
                <span
                  className={`text-[10px] font-bold uppercase tracking-widest px-2 py-1 rounded-md ${
                    TOPIC_STATUS_TONE[t.status] ?? 'bg-surface-container text-on-surface-variant'
                  }`}
                >
                  {TOPIC_STATUS_LABEL[t.status] ?? t.status}
                </span>
              </div>
              <p className="text-[11px] text-on-surface-variant mb-2">
                {t.questionsAsked} câu{t.followUpsUsed > 0 ? ` · ${t.followUpsUsed} câu nối tiếp` : ''}
                {typeof t.lastScore === 'number' ? ` · điểm câu cuối ${t.lastScore.toFixed(1)}` : ''}
              </p>
              {comment && (
                <p className="text-xs text-on-surface leading-relaxed">{comment}</p>
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
  const toneClass =
    tone === 'positive'
      ? 'text-emerald-600'
      : tone === 'negative'
        ? 'text-red-600'
        : 'text-secondary';
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6 mb-6">
      <div className={`flex items-center gap-2 mb-3 ${toneClass}`}>
        {icon}
        <h3 className="text-sm font-bold">{title}</h3>
      </div>
      <ul className="space-y-2">
        {items.map((item, i) => (
          <li key={i} className="flex items-start gap-2 text-sm text-on-surface">
            <CheckCircle2 className={`w-4 h-4 ${toneClass} flex-shrink-0 mt-0.5`} />
            <span className="leading-relaxed">{item}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
