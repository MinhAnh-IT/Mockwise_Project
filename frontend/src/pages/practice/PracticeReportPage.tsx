import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  Loader2,
  ThumbsDown,
  ThumbsUp,
  TrendingUp,
  Volume2,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getAnswer, getSession } from '@/api/interviews';
import { fetchAuthedBlobUrl } from '@/api/storage';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import { findPracticeOption } from '@/data/practice';
import type {
  AnswerView,
  PinnedQuestionView,
  SessionView,
  TopicProgress,
} from '@/types/interview';

const POLL_INTERVAL_MS = 3000;
const MAX_POLL_ATTEMPTS = 60; // ~3 phút

type OverallReview = {
  overallScore?: number;
  grade?: string;
  hireSignal?: string;
  summary?: string;
  strengths?: string[];
  weaknesses?: string[];
  perTopicSummary?: TopicSummaryEntry[];
  recommendations?: string[];
};

type TopicSummaryEntry = {
  topicKind?: string;
  topicValue?: string;
  status?: string;
  comment?: string;
};

const HIRE_LABEL: Record<string, string> = {
  strong_yes: 'Đề xuất rất nên tuyển',
  yes: 'Đề xuất tuyển',
  weak_yes: 'Có thể tuyển',
  no: 'Không nên tuyển',
  strong_no: 'Rất không nên tuyển',
};

const HIRE_TONE: Record<string, string> = {
  strong_yes: 'bg-emerald-100 text-emerald-700',
  yes: 'bg-emerald-50 text-emerald-700',
  weak_yes: 'bg-amber-50 text-amber-700',
  no: 'bg-red-50 text-red-700',
  strong_no: 'bg-red-100 text-red-700',
};

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
 * Final report. Polls GET /interviews/{sid} until status === SCORED, then
 * renders overallReview + per-topic progress + question list.
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

  const review = (session?.overallReview ?? null) as OverallReview | null;
  const isScored = session?.status === 'SCORED';

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
              Báo cáo phỏng vấn
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

          {isScored && session && session.questions.length > 0 && (
            <QuestionList sessionId={session.sessionId} questions={session.questions} />
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
  const hire = review.hireSignal;
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
          {hire && (
            <span
              className={`inline-block px-3 py-1 rounded-full text-xs font-bold ${
                HIRE_TONE[hire] ?? 'bg-surface-container text-on-surface-variant'
              }`}
            >
              {HIRE_LABEL[hire] ?? hire}
            </span>
          )}
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

function QuestionList({
  sessionId,
  questions,
}: {
  sessionId: string;
  questions: PinnedQuestionView[];
}) {
  const [openSqId, setOpenSqId] = useState<string | null>(null);
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6">
      <h3 className="text-sm font-bold text-on-surface mb-4">Danh sách câu hỏi</h3>
      <ol className="space-y-3">
        {questions.map((q) => (
          <QuestionItem
            key={q.sessionQuestionId}
            sessionId={sessionId}
            question={q}
            isOpen={openSqId === q.sessionQuestionId}
            onToggle={() =>
              setOpenSqId((curr) =>
                curr === q.sessionQuestionId ? null : q.sessionQuestionId,
              )
            }
          />
        ))}
      </ol>
    </div>
  );
}

type AnswerCacheEntry =
  | { state: 'loading' }
  | { state: 'ready'; answer: AnswerView }
  | { state: 'error'; message: string };

function QuestionItem({
  sessionId,
  question,
  isOpen,
  onToggle,
}: {
  sessionId: string;
  question: PinnedQuestionView;
  isOpen: boolean;
  onToggle: () => void;
}) {
  const [cache, setCache] = useState<AnswerCacheEntry | null>(null);
  const hasAnswer = !!question.latestAnswerId;

  useEffect(() => {
    if (!isOpen || !question.latestAnswerId || cache) return;
    let cancelled = false;
    setCache({ state: 'loading' });
    getAnswer(sessionId, question.latestAnswerId)
      .then((answer) => {
        if (!cancelled) setCache({ state: 'ready', answer });
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) {
          setCache(null);
          return;
        }
        const msg = err instanceof Error ? err.message : 'Không tải được câu trả lời.';
        setCache({ state: 'error', message: msg });
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, question.latestAnswerId, sessionId, cache]);

  return (
    <li className="border border-outline-variant/60 rounded-xl overflow-hidden">
      <button
        type="button"
        onClick={onToggle}
        disabled={!hasAnswer}
        aria-expanded={isOpen}
        className="w-full text-left p-4 flex items-start gap-3 hover:bg-surface-container-low/60 disabled:cursor-default disabled:hover:bg-transparent transition-colors"
      >
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1.5 flex-wrap">
            <span className="text-[10px] font-bold uppercase tracking-widest text-secondary bg-secondary/10 px-2 py-1 rounded-md">
              #{question.sequence}
            </span>
            {question.topicValue && (
              <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                {question.topicValue.replace(/_/g, ' ')}
              </span>
            )}
            {question.isFollowUp && (
              <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant bg-surface-container px-2 py-1 rounded-md">
                Nối tiếp
              </span>
            )}
            {!hasAnswer && (
              <span className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant bg-surface-container px-2 py-1 rounded-md">
                Không trả lời
              </span>
            )}
          </div>
          <p className="text-sm text-on-surface leading-relaxed">{question.text}</p>
        </div>
        {hasAnswer && (
          <ChevronDown
            className={`w-4 h-4 text-on-surface-variant flex-shrink-0 mt-1 transition-transform ${
              isOpen ? 'rotate-180' : ''
            }`}
          />
        )}
      </button>
      {isOpen && hasAnswer && (
        <div className="border-t border-outline-variant/60 p-4 bg-surface-container-low/40 space-y-4">
          {question.audioUrl && (
            <div>
              <p className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-2">
                <Volume2 className="w-3.5 h-3.5" />
                Nghe lại câu hỏi
              </p>
              <audio src={question.audioUrl} controls className="w-full" />
            </div>
          )}
          {cache?.state === 'loading' && (
            <div className="flex items-center gap-2 text-xs text-on-surface-variant">
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              Đang tải câu trả lời…
            </div>
          )}
          {cache?.state === 'error' && (
            <p className="text-xs text-red-600">{cache.message}</p>
          )}
          {cache?.state === 'ready' && <AnswerDetail answer={cache.answer} />}
        </div>
      )}
    </li>
  );
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

function AnswerDetail({ answer }: { answer: AnswerView }) {
  const verdict = (answer.verdict ?? {}) as Record<string, unknown>;
  const score = answer.score;
  const maxScore = answer.maxScore ?? 10;

  return (
    <div className="space-y-4">
      {answer.status === 'FAILED' && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700">
          Câu này không chấm được{answer.errorMessage ? `: ${answer.errorMessage}` : '.'}
        </div>
      )}

      {typeof score === 'number' && (
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-extrabold text-on-surface leading-none">
            {score.toFixed(1)}
          </span>
          <span className="text-xs text-on-surface-variant font-medium">
            / {maxScore.toFixed(0)}
          </span>
        </div>
      )}

      <VerdictChips verdict={verdict} />

      {answer.feedback && (
        <div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5">
            Phản hồi
          </p>
          <p className="text-sm text-on-surface leading-relaxed whitespace-pre-line">
            {answer.feedback}
          </p>
        </div>
      )}

      {answer.type === 'VIDEO' && answer.mediaUrl && (
        <div>
          <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5">
            Xem lại video bạn đã quay
          </p>
          <AnswerVideo mediaUrl={answer.mediaUrl} />
        </div>
      )}
    </div>
  );
}

function VerdictChips({ verdict }: { verdict: Record<string, unknown> }) {
  const entries = Object.entries(VERDICT_LABEL_TITLES)
    .map(([key, title]) => {
      const raw = verdict[key];
      if (typeof raw !== 'string') return null;
      const label = VERDICT_LABELS[key]?.[raw] ?? raw;
      return { key, title, label };
    })
    .filter((e): e is { key: string; title: string; label: string } => e !== null);
  if (entries.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {entries.map((e) => (
        <span
          key={e.key}
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-surface-container border border-outline-variant text-[11px] text-on-surface"
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

function AnswerVideo({ mediaUrl }: { mediaUrl: string }) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const createdRef = useRef<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    fetchAuthedBlobUrl(mediaUrl)
      .then((url) => {
        createdRef.current = url;
        setBlobUrl(url);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) return;
        setError(err instanceof Error ? err.message : 'Không tải được video.');
      });
  }, [mediaUrl]);

  useEffect(() => {
    load();
    // Browser keeps the blob bytes alive until revokeObjectURL — release on
    // unmount or when mediaUrl changes so a long history scroll doesn't
    // accumulate hundreds of MB of cached video.
    return () => {
      if (createdRef.current) {
        URL.revokeObjectURL(createdRef.current);
        createdRef.current = null;
      }
    };
  }, [load]);

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700">
        <p className="mb-1.5">{error}</p>
        <button
          type="button"
          onClick={load}
          className="text-red-700 underline font-semibold"
        >
          Thử lại
        </button>
      </div>
    );
  }
  if (!blobUrl) {
    return (
      <div className="flex items-center gap-2 text-xs text-on-surface-variant">
        <Loader2 className="w-3.5 h-3.5 animate-spin" />
        Đang tải video…
      </div>
    );
  }
  return (
    <video
      src={blobUrl}
      controls
      playsInline
      className="w-full max-h-[60vh] rounded-xl bg-black"
    />
  );
}
