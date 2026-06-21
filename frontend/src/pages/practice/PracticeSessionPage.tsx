import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  attachVideo,
  finishSession,
  getSession,
  submitAnswer,
} from '@/api/interviews';
import { uploadVideoPresigned } from '@/api/storage';
import QuestionCard from '@/components/practice/QuestionCard';
import SessionHeader from '@/components/practice/SessionHeader';
import VideoRecorder, { type RecorderSubmission } from '@/components/practice/VideoRecorder';
import { wordCount } from '@/lib/realtimeTranscriber';
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

/** Business code for SESSION_TIME_UP (interview-service StatusCode). */
const SESSION_TIME_UP_CODE = 4089;

/**
 * ANSWER_ALREADY_SUBMITTED — the server enforces one answer per question, so a
 * reload-during-waiting or a retried submit hits this. The answer is already
 * in, so we treat it as success and move to `waiting` (the next question / SCORED
 * arrives via polling).
 */
const ANSWER_ALREADY_SUBMITTED_CODE = 4100;

/** Whole-seconds remaining until an ISO deadline, floored at 0. */
function remainingSecs(deadlineIso: string): number {
  return Math.max(0, Math.floor((Date.parse(deadlineIso) - Date.now()) / 1000));
}

/**
 * Lever 2 background attach. After a fast-path submit, wire the video to the
 * already-scored answer once its upload composes. Best-effort and fully
 * detached from the critical path: if the streaming upload failed
 * ({@code uploadResult} → null) we fall back to a presigned PUT of the in-memory
 * blob so the candidate still gets a replay; any failure is swallowed (the
 * answer is already scored, and the attach endpoint is idempotent so a missed
 * one just means no replay video).
 */
async function attachVideoInBackground(
  sid: string,
  answerId: string,
  uploadResult: Promise<string | null>,
  blob: Blob,
  mimeType: string,
): Promise<void> {
  try {
    let objectId = await uploadResult;
    if (!objectId) {
      const stored = await uploadVideoPresigned(blob, sid, mimeType || undefined);
      objectId = stored.objectId;
    }
    await attachVideo(sid, answerId, objectId);
  } catch (e) {
    console.warn('[attach-video] background attach failed — answer already scored', e);
  }
}

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

  // One whole-session clock (no per-question limit). `deadline` is the
  // server-authoritative ISO instant = startedAt + timeBudgetMinutes; the FE
  // renders the countdown and ends the session when it hits zero. The server
  // also enforces it (rejects late submits with SESSION_TIME_UP) and a reaper
  // finalizes abandoned sessions, so this is UX, not the source of truth.
  const [deadline, setDeadline] = useState<string | null>(
    bootstrap?.deadlineAt ?? null,
  );
  const [secondsLeft, setSecondsLeft] = useState<number | null>(
    bootstrap?.deadlineAt ? remainingSecs(bootstrap.deadlineAt) : null,
  );
  const timeUpRef = useRef(false);

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
          // If the latest pinned question was already answered, the candidate
          // is mid-wait for the next one (or for scoring) — resume into
          // `waiting` so the poll picks it up, instead of re-recording an
          // already-submitted answer.
          if (latest.answered) setPhase('waiting');
        }
        setQuestionBudget(s.questionCount || latest?.sequence || 5);
        if (s.deadlineAt) {
          setDeadline(s.deadlineAt);
          setSecondsLeft(remainingSecs(s.deadlineAt));
        }
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

  // Session countdown — one ticking clock for the whole interview. Stops
  // once we're tearing down (finishing/finished) so it can't race the
  // finish→report navigation.
  useEffect(() => {
    if (!deadline) return;
    if (phase === 'finishing' || phase === 'finished') return;
    setSecondsLeft(remainingSecs(deadline));
    const id = window.setInterval(() => {
      setSecondsLeft(remainingSecs(deadline));
    }, 1000);
    return () => window.clearInterval(id);
  }, [deadline, phase]);

  // Warn before leaving/closing the tab mid-interview so a candidate doesn't
  // lose an in-progress recording by accident. The server-side reaper still
  // finalizes the session if they leave anyway, so this is a UX guard only.
  useEffect(() => {
    if (phase === 'finishing' || phase === 'finished') return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [phase]);

  const handleSubmit = useCallback(
    async (submission: RecorderSubmission) => {
      if (!sid || !current) return;
      const { blob, mimeType, transcript, uploadResult } = submission;
      const sqId = current.sessionQuestionId;
      setPhase('submitting');
      setError(null);

      // ── Lever 2 fast path ──────────────────────────────────────────────
      // A trusted real-time transcript → submit + score immediately, then
      // attach the video in the background once its upload composes. The user
      // stops waiting on the ~75% upload + ~15% batch-STT entirely.
      if (transcript) {
        // Log the transcript leaving the browser so the realtime path is easy
        // to confirm in DevTools alongside the backend "REALTIME TRANSCRIPT" log.
        console.info(
          `[realtime-stt] submitting transcript (${transcript.language}, ${wordCount(transcript.text)} words, ${transcript.durationMs}ms):`,
          transcript.text,
        );
        try {
          const out = await submitAnswer(sid, sqId, {
            type: 'VIDEO',
            transcriptText: transcript.text,
            transcriptLanguage: transcript.language,
            transcriptDurationMs: transcript.durationMs,
            transcriptSource: 'REALTIME_WEBSPEECH',
          });
          setPhase('waiting');
          // Detached from the critical path — a failure here only costs the
          // "watch replay" video, never the score.
          void attachVideoInBackground(sid, out.answerId, uploadResult, blob, mimeType);
          return;
        } catch (err) {
          if (err instanceof ApiError && err.code === SESSION_TIME_UP_CODE) {
            navigate(`/practice/${type}/session/${sid}/report`, { replace: true });
            return;
          }
          if (err instanceof ApiError && err.code === ANSWER_ALREADY_SUBMITTED_CODE) {
            setPhase('waiting');
            return;
          }
          const msg = err instanceof ApiError ? err.message
            : err instanceof Error ? err.message : 'Không gửi được câu trả lời.';
          setError(msg);
          setPhase('recording');
          return;
        }
      }

      // ── Legacy path (no real-time transcript) ──────────────────────────
      // Wait for the upload (streaming or fallback presigned PUT), then submit
      // with the storage objectId — the answer scores off batch STT.
      try {
        let storageObjectId = await uploadResult;
        if (!storageObjectId) {
          const stored = await uploadVideoPresigned(blob, sid, mimeType || undefined);
          storageObjectId = stored.objectId;
        }
        await submitAnswer(sid, sqId, {
          type: 'VIDEO',
          storageObjectId,
        });
        setPhase('waiting');
      } catch (err) {
        // Server clock ran out between render and submit — the session is
        // already finalized server-side; go straight to the report.
        if (err instanceof ApiError && err.code === SESSION_TIME_UP_CODE) {
          navigate(`/practice/${type}/session/${sid}/report`, { replace: true });
          return;
        }
        // Already answered (retry / double-submit) — the answer is in; just wait.
        if (err instanceof ApiError && err.code === ANSWER_ALREADY_SUBMITTED_CODE) {
          setPhase('waiting');
          return;
        }
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
    [sid, current, navigate, type],
  );

  const handleSubmitCode = useCallback(
    async (code: string, language: CodingLanguage) => {
      if (!sid || !current) return;
      setPhase('submitting');
      setError(null);
      try {
        const out = await submitAnswer(sid, current.sessionQuestionId, {
          type: 'CODE',
          code,
          language,
        });
        // CODING is non-adaptive: the server pins + returns the next problem
        // synchronously, so advance immediately (no 2s poll). When it's null
        // we just submitted the last problem → wait for scoring/finalize.
        if (out.nextQuestion) {
          lastSeenSqIdRef.current = out.nextQuestion.sessionQuestionId;
          setCurrent(out.nextQuestion);
          setPhase('recording');
        } else {
          setPhase('waiting');
        }
      } catch (err) {
        if (err instanceof ApiError && err.code === SESSION_TIME_UP_CODE) {
          navigate(`/practice/${type}/session/${sid}/report`, { replace: true });
          return;
        }
        if (err instanceof ApiError && err.code === ANSWER_ALREADY_SUBMITTED_CODE) {
          setPhase('waiting');
          return;
        }
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

  // Clock hit zero → end the session exactly like "Kết thúc sớm". Fires at
  // most once; the server-side guard + deadline reaper are the authoritative
  // backstop if the user's tab is closed or asleep.
  useEffect(() => {
    if (secondsLeft === null || secondsLeft > 0) return;
    if (timeUpRef.current) return;
    if (phase === 'finishing' || phase === 'finished') return;
    timeUpRef.current = true;
    void handleExit();
  }, [secondsLeft, phase, handleExit]);

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
      // For CODING the next problem is delivered synchronously on submit, so
      // reaching `waiting` means the last problem is in — we're finalizing.
      return (
        <div className="grid h-[100dvh] place-items-center bg-zinc-950 px-6 text-center">
          <p className="text-sm text-zinc-400">
            Đã nộp bài cuối. Đang chấm và tổng hợp báo cáo…
          </p>
        </div>
      );
    }
    return (
      <CodingWorkspace
        sessionId={sid}
        question={current}
        budget={questionBudget}
        secondsLeft={secondsLeft}
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
        secondsLeft={secondsLeft}
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

