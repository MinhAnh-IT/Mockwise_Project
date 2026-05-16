import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  finishSession,
  getSession,
  submitAnswer,
} from '@/api/interviews';
import { uploadVideoPresigned } from '@/api/storage';
import QuestionCard from '@/components/practice/QuestionCard';
import SessionHeader from '@/components/practice/SessionHeader';
import VideoRecorder from '@/components/practice/VideoRecorder';
import WaitingNext from '@/components/practice/WaitingNext';
import CodingWorkspace from '@/components/practice/coding/CodingWorkspace';
import { findPracticeOption } from '@/data/practice';
import type { CodingLanguage } from '@/types/coding';
import type { PinnedQuestionView, StartSessionOutput } from '@/types/interview';

type Phase = 'recording' | 'submitting' | 'waiting' | 'finishing' | 'finished';

type LocationState = {
  bootstrap?: StartSessionOutput;
};

const POLL_INTERVAL_MS = 2000;

/**
 * Live interview screen. State machine:
 *
 *   recording  ──user nộp──►  submitting  ──upload+API──►  waiting
 *   waiting    ──poll mới──►  recording  (pin câu kế)
 *   waiting    ──SCORED──►   navigate /report
 *   *          ──Kết thúc──►  finishing  ──poll SCORED──►  /report
 *
 * The first pinned question is passed via navigation state from the intro
 * page so we don't have to wait on a network round trip. On a hard refresh
 * we fall back to GET /sessions/{sid} and use the latest pinned question.
 */
export default function PracticeSessionPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { type, sid } = useParams<{ type: string; sid: string }>();
  const option = type ? findPracticeOption(type) : undefined;

  const bootstrap = (location.state as LocationState | null)?.bootstrap;

  const [phase, setPhase] = useState<Phase>('recording');
  const [current, setCurrent] = useState<PinnedQuestionView | null>(
    bootstrap?.firstQuestion ?? null,
  );
  const [questionBudget, setQuestionBudget] = useState<number>(
    bootstrap?.questionBudget ?? 5,
  );
  const [error, setError] = useState<string | null>(null);
  const [bootstrapLoaded, setBootstrapLoaded] = useState<boolean>(!!bootstrap);

  // Track the sessionQuestionId we're waiting to advance past so the polling
  // loop knows which sequence is "old" vs "new".
  const lastSeenSqIdRef = useRef<string | null>(bootstrap?.firstQuestion.sessionQuestionId ?? null);

  // Hydrate from server when there's no nav state (refresh / direct link).
  useEffect(() => {
    if (bootstrap || !sid) return;
    let cancelled = false;
    (async () => {
      try {
        const s = await getSession(sid);
        if (cancelled) return;
        if (s.status === 'SCORED') {
          navigate(`/practice/${type}/session/${sid}/report`, { replace: true });
          return;
        }
        if (s.status === 'CANCELLED' || s.status === 'COMPLETED') {
          // Wait for scoring to finish in the report page; report handles it.
          navigate(`/practice/${type}/session/${sid}/report`, { replace: true });
          return;
        }
        const latest = s.questions[s.questions.length - 1];
        if (latest) {
          setCurrent(latest);
          lastSeenSqIdRef.current = latest.sessionQuestionId;
        }
        setQuestionBudget(s.questionCount || latest?.sequence || 5);
        setBootstrapLoaded(true);
      } catch (err) {
        const msg = err instanceof ApiError ? err.message : 'Không tải được phiên phỏng vấn.';
        setError(msg);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [bootstrap, sid, navigate, type]);

  // Polling loop while waiting for the next pinned question or the SCORED
  // transition. Only runs in `waiting` / `finishing` phases.
  useEffect(() => {
    if (!sid) return;
    if (phase !== 'waiting' && phase !== 'finishing') return;

    let cancelled = false;
    let timer: number | null = null;

    const tick = async () => {
      try {
        const s = await getSession(sid);
        if (cancelled) return;
        if (s.status === 'SCORED' || s.status === 'COMPLETED' || s.status === 'CANCELLED') {
          // Report page handles "scoring in progress" if we're not yet SCORED.
          navigate(`/practice/${type}/session/${sid}/report`, { replace: true });
          return;
        }
        if (phase === 'waiting') {
          const latest = s.questions[s.questions.length - 1];
          if (latest && latest.sessionQuestionId !== lastSeenSqIdRef.current) {
            // Planner pinned a new question — advance.
            lastSeenSqIdRef.current = latest.sessionQuestionId;
            setCurrent(latest);
            setPhase('recording');
            return;
          }
        }
      } catch (err) {
        // Soft-fail: a single 5xx during polling shouldn't break the page.
        // Caller can keep polling on the next tick. Surface only if we never
        // recover (handled by the user clicking exit).
        if (err instanceof ApiError && err.status === 401) {
          // Auth blew up — the API client already handled refresh; if we're
          // still 401 the auth context flips us to /login.
          return;
        }
      }
      if (!cancelled) timer = window.setTimeout(tick, POLL_INTERVAL_MS);
    };

    timer = window.setTimeout(tick, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      if (timer !== null) window.clearTimeout(timer);
    };
  }, [phase, sid, navigate, type]);

  const handleSubmit = useCallback(
    async (blob: Blob, mimeType: string, uploadedObjectId: string | null) => {
      if (!sid || !current) return;
      setPhase('submitting');
      setError(null);
      try {
        // Fast path: the recorder streamed the clip to MinIO *during*
        // recording and storage already composed it — just submit the id,
        // no post-"Nộp" transfer. Fallback path (streaming unavailable or
        // failed): the legacy presigned-PUT of the in-memory blob.
        let storageObjectId = uploadedObjectId;
        if (!storageObjectId) {
          const stored = await uploadVideoPresigned(blob, sid, mimeType || undefined);
          storageObjectId = stored.objectId;
        }
        await submitAnswer(sid, current.sessionQuestionId, {
          type: 'VIDEO',
          storageObjectId,
        });
        setPhase('waiting');
      } catch (err) {
        const msg =
          err instanceof ApiError
            ? err.message
            : err instanceof Error
              ? err.message
              : 'Không gửi được câu trả lời.';
        setError(msg);
        // Roll back to recording so user can try again. The recorder mounts
        // fresh which re-requests the camera; that's acceptable for a rare
        // failure path.
        setPhase('recording');
      }
    },
    [sid, current],
  );

  const handleSubmitCode = useCallback(
    async (code: string, language: CodingLanguage) => {
      if (!sid || !current) return;
      setPhase('submitting');
      setError(null);
      try {
        await submitAnswer(sid, current.sessionQuestionId, {
          type: 'CODE',
          code,
          language,
        });
        setPhase('waiting');
      } catch (err) {
        const msg =
          err instanceof ApiError
            ? err.message
            : err instanceof Error
              ? err.message
              : 'Không nộp được bài code.';
        setError(msg);
        setPhase('recording');
      }
    },
    [sid, current],
  );

  const handleExit = useCallback(async () => {
    if (!sid) return;
    setPhase('finishing');
    try {
      await finishSession(sid);
    } catch (err) {
      // /finish is idempotent on the backend; swallow and let polling pick up
      // the SCORED transition. Still surface on hard auth failure.
      if (err instanceof ApiError && err.status === 401) {
        setError(err.message);
        setPhase('recording');
        return;
      }
    }
    // Polling loop now waits for SCORED and navigates to /report.
  }, [sid]);

  if (!option || !sid) {
    navigate('/practice', { replace: true });
    return null;
  }
  if (option.interviewType === null) {
    navigate('/practice', { replace: true });
    return null;
  }

  if (!current && !bootstrapLoaded) {
    return <FullPageLoading label="Đang tải phiên phỏng vấn…" />;
  }

  if (!current) {
    return (
      <FullPageError
        message={error ?? 'Không tìm thấy câu hỏi cho phiên này.'}
        onBack={() => navigate('/practice')}
      />
    );
  }

  // LIVE_CODING gets the full-screen LeetCode-style workspace instead of
  // the SessionHeader + QuestionCard + VideoRecorder stack.
  if (current.questionType === 'LIVE_CODING') {
    if (phase === 'waiting' || phase === 'finishing') {
      return (
        <div className="grid h-screen place-items-center bg-zinc-950 px-6 text-center">
          <p className="text-sm text-zinc-400">
            {phase === 'finishing'
              ? 'Đang kết thúc phiên và tổng hợp báo cáo…'
              : `Đã nộp câu ${current.sequence}. Đang chờ câu tiếp theo…`}
          </p>
        </div>
      );
    }
    return (
      <CodingWorkspace
        sessionId={sid}
        question={current}
        budget={questionBudget}
        busy={phase === 'submitting'}
        onSubmit={handleSubmitCode}
        onExit={handleExit}
      />
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <SessionHeader
        title={option.title}
        current={current.sequence}
        budget={questionBudget}
        onExit={handleExit}
        exiting={phase === 'finishing'}
      />

      <main className="flex-1 px-6 py-6">
        <div className="max-w-4xl mx-auto space-y-5">
          <QuestionCard question={current} />

          {error && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-4 flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-bold text-red-700 mb-0.5">Có lỗi xảy ra</p>
                <p className="text-xs text-red-600 leading-relaxed">{error}</p>
              </div>
            </div>
          )}

          {phase === 'waiting' || phase === 'finishing' ? (
            <WaitingNext
              hint={
                phase === 'finishing'
                  ? 'Đang kết thúc phiên và tổng hợp báo cáo…'
                  : `Đã nộp câu ${current.sequence}.`
              }
            />
          ) : (
            // No `key` here on purpose — the recorder stays mounted across
            // questions so the camera/mic stream isn't re-acquired each
            // round. Internal phase resets when `question.sessionQuestionId`
            // changes.
            <VideoRecorder
              question={current}
              maxSeconds={option.recordingMaxSeconds}
              sessionId={sid ?? ''}
              busy={phase === 'submitting'}
              onSubmit={handleSubmit}
            />
          )}
        </div>
      </main>
    </div>
  );
}

function FullPageLoading({ label }: { label: string }) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface">
      <p className="text-sm text-on-surface-variant">{label}</p>
    </div>
  );
}

function FullPageError({ message, onBack }: { message: string; onBack: () => void }) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface px-6">
      <div className="max-w-md text-center">
        <AlertTriangle className="w-10 h-10 text-red-500 mx-auto mb-3" />
        <p className="text-sm text-on-surface-variant mb-4">{message}</p>
        <button
          type="button"
          onClick={onBack}
          className="px-4 py-2 rounded-xl bg-secondary text-on-secondary text-sm font-semibold"
        >
          Về trang luyện tập
        </button>
      </div>
    </div>
  );
}

