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
            <QuestionPager questions={session.questions} onReload={loadSession} />
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

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6">
      <div className="flex items-center justify-between mb-4 gap-3">
        <h3 className="text-sm font-bold text-on-surface">Chi tiết từng câu</h3>
        <span className="text-xs font-semibold text-on-surface-variant">
          Câu {safeIndex + 1} / {total}
        </span>
      </div>

      <QuestionPanel question={current} onReload={onReload} />

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
            <AnswerDetail answer={answer} onReload={onReload} />
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

function AnswerDetail({
  answer,
  onReload,
}: {
  answer: AnswerView;
  onReload: () => Promise<SessionView | null>;
}) {
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

      <EvaluationBreakdown detail={answer.evaluationDetail} />

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
          <AnswerVideo mediaUrl={answer.mediaUrl} onReload={onReload} />
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

function ScoreBars({ scores }: { scores: Record<string, number | null> | null }) {
  if (!scores) return null;
  const entries = Object.entries(scores).filter(
    ([, v]) => typeof v === 'number',
  ) as [string, number][];
  if (entries.length === 0) return null;
  return (
    <div className="space-y-2">
      {entries.map(([key, value]) => (
        <div key={key} className="flex items-center gap-3">
          <span className="text-[11px] font-semibold text-on-surface-variant w-40 shrink-0">
            {DIMENSION_LABELS[key] ?? key}
          </span>
          <div className="flex-1 h-2 rounded-full bg-surface-container overflow-hidden">
            <div
              className="h-full bg-secondary rounded-full"
              style={{ width: `${Math.max(0, Math.min(100, value))}%` }}
            />
          </div>
          <span className="text-[11px] font-bold text-on-surface w-10 text-right tabular-nums">
            {value}
          </span>
        </div>
      ))}
    </div>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-1.5">
      {children}
    </p>
  );
}

function BehavioralBreakdown({ detail }: { detail: BehavioralEvaluationDetail }) {
  return (
    <div className="space-y-4">
      {detail.scores && (
        <div>
          <SectionTitle>Điểm theo tiêu chí</SectionTitle>
          <ScoreBars scores={detail.scores} />
        </div>
      )}
      {detail.signalCoverage.length > 0 && (
        <div>
          <SectionTitle>Tín hiệu mong đợi</SectionTitle>
          <ul className="space-y-1">
            {detail.signalCoverage.map((s) => (
              <li
                key={s.signalName}
                className="flex items-center gap-2 text-xs text-on-surface"
              >
                <span
                  className={
                    s.detected
                      ? 'inline-block w-1.5 h-1.5 rounded-full bg-green-500'
                      : 'inline-block w-1.5 h-1.5 rounded-full bg-on-surface-variant/40'
                  }
                />
                <span>{s.signalName.replace(/_/g, ' ')}</span>
                <span className="text-on-surface-variant">
                  {s.detected ? '— có' : '— chưa rõ'}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {detail.redFlags.length > 0 && (
        <div>
          <SectionTitle>Điểm cần lưu ý</SectionTitle>
          <ul className="space-y-1">
            {detail.redFlags.map((f, i) => (
              <li key={`${f.type}-${i}`} className="text-xs text-on-surface">
                <span className="font-semibold">{f.type.replace(/_/g, ' ')}</span>
                <span className="text-on-surface-variant">
                  {' '}
                  · mức độ {SEVERITY_LABEL[f.severity?.toLowerCase()] ?? f.severity}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function ConceptualBreakdown({ detail }: { detail: ConceptualEvaluationDetail }) {
  return (
    <div className="space-y-4">
      {detail.scores && (
        <div>
          <SectionTitle>Điểm theo tiêu chí</SectionTitle>
          <ScoreBars scores={detail.scores} />
        </div>
      )}
      {detail.conceptCoverage.length > 0 && (
        <div>
          <SectionTitle>Khái niệm cốt lõi</SectionTitle>
          <ul className="space-y-1">
            {detail.conceptCoverage.map((c) => {
              const status = !c.mentioned
                ? { color: 'bg-on-surface-variant/40', text: 'không nhắc tới' }
                : c.correct === false
                  ? { color: 'bg-red-500', text: 'nói sai' }
                  : c.correct === true
                    ? { color: 'bg-green-500', text: 'đúng' }
                    : { color: 'bg-amber-500', text: 'có nhắc, chưa rõ đúng/sai' };
              return (
                <li
                  key={c.conceptName}
                  className="flex items-center gap-2 text-xs text-on-surface"
                >
                  <span className={`inline-block w-1.5 h-1.5 rounded-full ${status.color}`} />
                  <span>{c.conceptName.replace(/_/g, ' ')}</span>
                  <span className="text-on-surface-variant">— {status.text}</span>
                </li>
              );
            })}
          </ul>
        </div>
      )}
      {detail.misconceptions.length > 0 && (
        <div>
          <SectionTitle>Hiểu nhầm cần sửa</SectionTitle>
          <ul className="space-y-1 list-disc list-inside">
            {detail.misconceptions.map((m, i) => (
              <li key={i} className="text-xs text-on-surface">
                {m.claim}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function LiveCodingBreakdown({ detail }: { detail: LiveCodingEvaluationDetail }) {
  return (
    <div className="space-y-4">
      {detail.scores && (
        <div>
          <SectionTitle>Điểm theo tiêu chí</SectionTitle>
          <ScoreBars scores={detail.scores} />
        </div>
      )}
      {typeof detail.isOptimal === 'boolean' && (
        <p className="text-xs text-on-surface">
          <span className="font-semibold">Giải pháp tối ưu:</span>{' '}
          <span className="text-on-surface-variant">
            {detail.isOptimal ? 'Có' : 'Chưa'}
          </span>
        </p>
      )}
      {detail.codeIssues.length > 0 && (
        <div>
          <SectionTitle>Vấn đề trong code</SectionTitle>
          <ul className="space-y-1">
            {detail.codeIssues.map((i, idx) => (
              <li key={`${i.type}-${idx}`} className="text-xs text-on-surface">
                <span className="font-semibold">{i.type.replace(/_/g, ' ')}</span>
                {i.detail && (
                  <>
                    <span className="text-on-surface-variant"> · {i.detail}</span>
                  </>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
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
  const [errored, setErrored] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [reloadFailed, setReloadFailed] = useState(false);

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
      <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-xs text-red-700">
        <p className="mb-1.5">
          {reloadFailed
            ? 'Không tải lại được video. Vui lòng thử lại sau.'
            : 'Không tải được video. URL có thể đã hết hạn.'}
        </p>
        <button
          type="button"
          onClick={retry}
          disabled={reloading}
          className="text-red-700 underline font-semibold disabled:opacity-50"
        >
          {reloading ? 'Đang thử lại…' : 'Thử lại'}
        </button>
      </div>
    );
  }
  return (
    <video
      // key forces a fresh element when the URL changes after onReload,
      // otherwise <video> would stick with the previous (expired) src.
      key={mediaUrl}
      src={mediaUrl}
      controls
      playsInline
      onError={() => setErrored(true)}
      className="w-full max-h-[60vh] rounded-xl bg-black"
    />
  );
}
