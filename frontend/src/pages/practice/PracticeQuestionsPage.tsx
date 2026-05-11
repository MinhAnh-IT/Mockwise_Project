import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Volume2,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getSession } from '@/api/interviews';
import { fetchAuthedBlobUrl } from '@/api/storage';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import { findPracticeOption } from '@/data/practice';
import type {
  AnswerView,
  AssessmentVerdict,
  PinnedQuestionView,
  SessionView,
} from '@/types/interview';

const POLL_INTERVAL_MS = 3000;
const MAX_POLL_ATTEMPTS = 60;

/**
 * Per-question detail. Paginated 1-question-per-page with Prev/Next.
 * Sibling of {@code PracticeReportPage} which shows the overall summary.
 */
export default function PracticeQuestionsPage() {
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
  }, [sid]);

  if (!option || !sid) {
    navigate('/practice', { replace: true });
    return null;
  }

  const isScored = session?.status === 'SCORED';
  const reportHref = `/practice/${type}/session/${sid}/report`;

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />

      <main className="flex-1 px-6 pt-24 pb-16">
        <div className="max-w-3xl mx-auto">
          <Link
            to={reportHref}
            className="inline-flex items-center gap-1 text-sm font-medium text-on-surface-variant hover:text-on-surface mb-4 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Về trang tổng quan
          </Link>

          <div className="mb-6">
            <p className="text-[10px] font-bold uppercase tracking-widest text-secondary mb-2">
              Chi tiết câu hỏi
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
            <StatusPlaceholder status={session?.status} error={error} />
          )}

          {isScored && session && session.questions.length > 0 && (
            <QuestionPager questions={session.questions} />
          )}

          {isScored && session && session.questions.length === 0 && (
            <p className="text-sm text-on-surface-variant text-center py-12">
              Phiên này chưa có câu hỏi nào.
            </p>
          )}
        </div>
      </main>

      <Footer />
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
        <p className="text-sm text-red-700 font-semibold mb-2">Có lỗi tải chi tiết</p>
        <p className="text-xs text-red-600 leading-relaxed">{error}</p>
      </div>
    );
  }
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-10 text-center">
      <Loader2 className="w-8 h-8 mx-auto mb-4 text-secondary animate-spin" />
      <h3 className="text-base font-bold text-on-surface mb-2">
        {status === 'COMPLETED' || status === 'CANCELLED'
          ? 'Đang tổng hợp chi tiết…'
          : 'Đang chấm các câu trả lời…'}
      </h3>
      <p className="text-xs text-on-surface-variant max-w-md mx-auto leading-relaxed">
        Vui lòng quay lại sau khi hệ thống chấm xong.
      </p>
    </div>
  );
}

function QuestionPager({
  questions,
}: {
  questions: PinnedQuestionView[];
}) {
  const [index, setIndex] = useState(0);
  const safeIndex = Math.min(index, questions.length - 1);
  const current = questions[safeIndex];
  const total = questions.length;
  const goPrev = () => setIndex((i) => Math.max(0, i - 1));
  const goNext = () => setIndex((i) => Math.min(total - 1, i + 1));

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6">
      <div className="flex items-center justify-between mb-4 gap-3">
        <h3 className="text-sm font-bold text-on-surface">Chi tiết từng câu</h3>
        <span className="text-xs font-semibold text-on-surface-variant">
          Câu {safeIndex + 1} / {total}
        </span>
      </div>

      <QuestionPanel question={current} />

      <div className="mt-5 flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={goPrev}
          disabled={safeIndex === 0}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl border border-outline-variant text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <ChevronLeft className="w-4 h-4" />
          Câu trước
        </button>
        <button
          type="button"
          onClick={goNext}
          disabled={safeIndex >= total - 1}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-secondary text-on-secondary text-sm font-semibold hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Câu tiếp
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

function QuestionPanel({ question }: { question: PinnedQuestionView }) {
  const answer = question.answer;
  const hasAnswer = !!(answer ?? question.latestAnswerId);

  return (
    <div className="border border-outline-variant/60 rounded-xl overflow-hidden">
      <div className="p-4 bg-surface-container-low/30">
        <div className="flex items-center gap-2 mb-2 flex-wrap">
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
        {hasAnswer ? (
          answer ? (
            <AnswerDetail answer={answer} />
          ) : (
            <p className="text-xs text-on-surface-variant italic">
              Đang chuẩn bị chi tiết câu trả lời…
            </p>
          )
        ) : (
          <p className="text-xs text-on-surface-variant italic">
            Ứng viên không trả lời câu này.
          </p>
        )}
      </div>
    </div>
  );
}

function AnswerDetail({ answer }: { answer: AnswerView }) {
  const verdict = answer.verdict;
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
