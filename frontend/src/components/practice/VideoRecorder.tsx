import { useCallback, useEffect, useRef, useState } from 'react';
import { Camera, Mic, MicOff, Pause, Square, VideoOff, Volume2 } from 'lucide-react';
import type { PinnedQuestionView } from '@/types/interview';
import { createStreamingVideoUpload, type StreamingVideoUpload } from '@/api/storage';

type Phase =
  | 'requesting'
  | 'denied'
  | 'intro'
  | 'recording'
  | 'submitting';

type Props = {
  /**
   * The pinned question on screen. The component watches its
   * {@code sessionQuestionId} so the parent can swap questions without
   * unmounting — that keeps the camera/mic stream alive between rounds.
   */
  question: PinnedQuestionView;
  /** Wall-clock cap in seconds. Hitting it auto-stops + auto-submits. */
  maxSeconds: number;
  /**
   * Owning interview session id — used to open the streaming
   * (upload-while-recording) upload so the clip transfers during the
   * recording instead of after "Nộp".
   */
  sessionId: string;
  /**
   * True while the parent is uploading + calling /answers. Disables UI and
   * suppresses any further state transitions until the parent moves on.
   */
  busy: boolean;
  /**
   * Fired exactly once per recording (manual stop or auto-stop).
   * {@code uploadedObjectId} is the storage object id when the parts
   * streamed during recording composed successfully — the parent then
   * skips the post-submit upload. {@code null} means streaming was
   * unavailable/failed; the parent must upload {@code blob} itself.
   */
  onSubmit: (blob: Blob, mimeType: string, uploadedObjectId: string | null) => void;
};

/**
 * Auto-flow recorder:
 *
 *   getUserMedia → intro (TTS auto-plays) → recording (MediaRecorder.start)
 *      → user clicks "Kết thúc & nộp" or timer hits cap → onSubmit(blob)
 *
 * Edge cases handled:
 *  - {@code question.audioUrl} missing → skip TTS, start recording.
 *  - Browser blocks autoplay → fall back to "Nghe câu hỏi & bắt đầu" button;
 *    audio still plays after the click and recording starts on `ended`.
 *  - Audio errors mid-playback → skip the rest, start recording.
 *  - User clicks "Bắt đầu trả lời ngay" during TTS → stop audio, start
 *    recording immediately.
 *  - Parent unmounts during recording (e.g. user clicks "Kết thúc sớm") →
 *    cleanup pre-emptively marks the recording as already-submitted so the
 *    pending {@code onstop} handler does NOT fire a stale upload.
 *  - Question swap mid-recording (shouldn't happen — parent renders
 *    {@code WaitingNext} during submit) is defensively handled by treating
 *    the in-flight recording as exiting.
 *
 * The codec hint passed to MediaRecorder may include {@code ;codecs=...}
 * but the {@code Blob.type} and the value reported to {@code onSubmit} only
 * carry the base MIME — storage-service rejects parameters and the codec
 * is already baked into the bytes.
 */
const MIME_CANDIDATES = [
  'video/webm;codecs=vp9,opus',
  'video/webm;codecs=vp8,opus',
  'video/webm',
];

type RecorderMime = { recorder: string; base: string };

function pickMimeType(): RecorderMime {
  if (typeof MediaRecorder === 'undefined') {
    return { recorder: 'video/webm', base: 'video/webm' };
  }
  for (const m of MIME_CANDIDATES) {
    if (MediaRecorder.isTypeSupported(m)) {
      return { recorder: m, base: m.split(';')[0].trim() };
    }
  }
  return { recorder: 'video/webm', base: 'video/webm' };
}

export default function VideoRecorder({ question, maxSeconds, sessionId, busy, onSubmit }: Props) {
  const [phase, setPhase] = useState<Phase>('requesting');
  const [permissionError, setPermissionError] = useState<string | null>(null);
  const [audioBlocked, setAudioBlocked] = useState(false);
  const [audioReplaying, setAudioReplaying] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [micLevel, setMicLevel] = useState(0); // 0..1

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  // Streaming upload for the current recording (null when unavailable —
  // finalise() then falls back to a single post-stop upload).
  const uploaderRef = useRef<StreamingVideoUpload | null>(null);
  const mimeTypeRef = useRef<RecorderMime>({ recorder: 'video/webm', base: 'video/webm' });
  const timerRef = useRef<number | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const rafRef = useRef<number | null>(null);
  // Set to `true` once a recording has been finalised (submit OR discard).
  // Any later `onstop` handler is a no-op so we never fire `onSubmit` twice
  // — this is what makes both the normal stop path and the unmount path
  // safe.
  const finalisedRef = useRef(false);
  // Latest `busy` value, accessible from within MediaRecorder callbacks
  // without re-binding them on every render.
  const busyRef = useRef(busy);
  useEffect(() => {
    busyRef.current = busy;
  }, [busy]);

  // ── Stream lifecycle (mount once, dispose on unmount) ────────────────────

  const requestStream = useCallback(async () => {
    setPhase('requesting');
    setPermissionError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: true,
      });
      streamRef.current = stream;
      // Don't bind srcObject here — the <video> element is gated by
      // `phase !== 'requesting'` and isn't in the DOM yet, so videoRef
      // is still null. The dedicated effect below picks up the binding
      // once React re-renders with phase='intro'.
      // Mic level meter — view-only analyser, not tied to MediaRecorder.
      const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const ctx = new Ctx();
      const source = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 512;
      source.connect(analyser);
      audioCtxRef.current = ctx;
      const buf = new Uint8Array(analyser.frequencyBinCount);
      const tick = () => {
        analyser.getByteTimeDomainData(buf);
        let sum = 0;
        for (let i = 0; i < buf.length; i++) {
          const v = (buf[i] - 128) / 128;
          sum += v * v;
        }
        const rms = Math.sqrt(sum / buf.length);
        setMicLevel(Math.min(1, rms * 3));
        rafRef.current = requestAnimationFrame(tick);
      };
      rafRef.current = requestAnimationFrame(tick);
      setPhase('intro');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setPermissionError(msg);
      setPhase('denied');
    }
  }, []);

  useEffect(() => {
    requestStream();
    return () => {
      // Pre-emptively mark any in-flight recording as finalised so the
      // MediaRecorder.onstop callback (which fires asynchronously after
      // recorder.stop()) does NOT call `onSubmit` with a stale blob from
      // a session the user has already left.
      finalisedRef.current = true;
      if (timerRef.current !== null) window.clearInterval(timerRef.current);
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
      audioCtxRef.current?.close().catch(() => {});
      const a = audioRef.current;
      if (a) {
        a.pause();
        a.src = '';
      }
      streamRef.current?.getTracks().forEach((t) => t.stop());
      const rec = recorderRef.current;
      if (rec && rec.state !== 'inactive') {
        try {
          rec.stop();
        } catch {
          /* noop */
        }
      }
    };
  }, [requestStream]);

  // ── Recording control ─────────────────────────────────────────────────────

  const finalise = useCallback(() => {
    if (finalisedRef.current) return;
    finalisedRef.current = true;
    const base = mimeTypeRef.current.base;
    const blob = new Blob(chunksRef.current, { type: base });
    chunksRef.current = [];
    const uploader = uploaderRef.current;
    uploaderRef.current = null;
    if (!uploader) {
      onSubmit(blob, base, null);
      return;
    }
    // Parts were streamed during recording. If they compose OK, hand the
    // parent the ready objectId so it skips the post-submit upload; any
    // failure → null → parent falls back to uploadVideoPresigned(blob).
    // [TIMING] Wall-clock the user waits on after hitting stop: flush of the
    // last part + the compose round-trip. Open DevTools console while testing.
    const _t0 = performance.now();
    uploader
      .finish()
      .then((objectId) => {
        console.info(`[TIMING] upload_finish ${Math.round(performance.now() - _t0)}ms blobBytes=${blob.size}`);
        onSubmit(blob, base, objectId);
      })
      .catch(() => {
        console.info(`[TIMING] upload_finish_failed ${Math.round(performance.now() - _t0)}ms blobBytes=${blob.size}`);
        onSubmit(blob, base, null);
      });
  }, [onSubmit]);

  const stopRecording = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    // Cut off any in-flight question replay so the candidate's outgoing
    // upload doesn't carry a few extra seconds of TTS audio they didn't
    // want — and so the next question's intro audio starts from a clean
    // slate.
    const a = audioRef.current;
    if (a && !a.paused) {
      a.pause();
      a.currentTime = 0;
    }
    setAudioReplaying(false);
    setPhase('submitting');
    const rec = recorderRef.current;
    if (rec && rec.state !== 'inactive') {
      // Blob is built in onstop; finalise() is called there.
      try {
        rec.stop();
      } catch {
        finalise();
      }
    } else {
      finalise();
    }
  }, [finalise]);

  const startRecording = useCallback(() => {
    const stream = streamRef.current;
    if (!stream) return;
    if (recorderRef.current && recorderRef.current.state !== 'inactive') {
      return; // already recording
    }
    finalisedRef.current = false;
    chunksRef.current = [];
    const mime = pickMimeType();
    mimeTypeRef.current = mime;
    // Open a streaming upload so parts transfer while recording instead of
    // after "Nộp". Purely additive — chunksRef still accumulates the full
    // blob as a guaranteed fallback if any part fails (see finalise()).
    try {
      uploaderRef.current = sessionId
        ? createStreamingVideoUpload(sessionId, mime.base)
        : null;
    } catch {
      uploaderRef.current = null;
    }
    const rec = new MediaRecorder(stream, { mimeType: mime.recorder });
    rec.ondataavailable = (e) => {
      if (e.data && e.data.size > 0) {
        chunksRef.current.push(e.data);
        uploaderRef.current?.push(e.data);
      }
    };
    rec.onstop = () => {
      finalise();
    };
    rec.onerror = () => {
      // Surface the failure as a forced stop. Downstream sees the same
      // onSubmit call the happy path uses, with whatever bytes we managed
      // to capture — better a partial blob than nothing.
      finalise();
    };
    rec.start(1000); // chunk every second so a crash leaves something useful
    recorderRef.current = rec;
    setElapsed(0);
    // `phase` is already 'recording' when the phase-effect drives us here.
    timerRef.current = window.setInterval(() => {
      setElapsed((prev) => {
        const next = prev + 1;
        if (next >= maxSeconds) {
          // Hit cap. Defer by a tick so this state update completes first.
          window.setTimeout(() => {
            if (!finalisedRef.current) stopRecording();
          }, 0);
        }
        return next;
      });
    }, 1000);
  }, [finalise, maxSeconds, stopRecording, sessionId]);

  // ── Bind stream to <video> once both are available ───────────────────────
  // The <video> tag is gated by `phase !== 'requesting'`, so it doesn't
  // exist in the DOM at the moment getUserMedia resolves. We re-attempt the
  // bind after every render that has the element AND a live stream — set
  // srcObject only when it's not already pointing at the stream so we don't
  // restart the underlying video element on every render.
  useEffect(() => {
    const el = videoRef.current;
    const stream = streamRef.current;
    if (!el || !stream) return;
    if (el.srcObject !== stream) {
      el.srcObject = stream;
    }
  });

  // ── Per-question reset clears the replay flag ──────────────────────────
  // Reset audio replay state on question swap (kept above the main reset
  // effect so the latter can drive phase transitions without races).
  // ── Per-question reset ────────────────────────────────────────────────────
  // When the parent swaps in a new question, snap back to `intro`. Bail on
  // the very first mount (phase still `requesting`) — the post-stream
  // transition into `intro` is driven by `requestStream` itself, and the
  // intro-effect below picks up from there.
  useEffect(() => {
    if (phase === 'requesting' || phase === 'denied') return;
    const rec = recorderRef.current;
    if (rec && rec.state !== 'inactive') {
      finalisedRef.current = true; // discard the previous question's bytes
      try {
        rec.stop();
      } catch {
        /* noop */
      }
    }
    setAudioBlocked(false);
    setAudioReplaying(false);
    setElapsed(0);
    finalisedRef.current = false;
    setPhase('intro');
    // Ignore phase in deps — only react to the question swap. We set phase
    // explicitly here.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [question.sessionQuestionId]);

  // ── Intro phase: try to autoplay TTS ─────────────────────────────────────
  // Runs whenever we (re)enter `intro` — both on the post-stream transition
  // for the first question and on every subsequent question swap. If the
  // browser blocks autoplay, we leave a manual play button up; if there's
  // no audio at all, we skip straight to recording.
  useEffect(() => {
    if (phase !== 'intro') return;
    const a = audioRef.current;
    if (!a) return;

    a.pause();
    a.currentTime = 0;

    if (!question.audioUrl) {
      setPhase('recording');
      return;
    }

    a.src = question.audioUrl;
    const playPromise = a.play();
    if (playPromise && typeof playPromise.then === 'function') {
      playPromise
        .then(() => {
          // Playing — recording starts when audio fires `ended`.
        })
        .catch(() => {
          // Autoplay blocked. Recording does NOT auto-start until the user
          // either taps "Bắt đầu trả lời ngay" (skips TTS) or "Nghe câu hỏi
          // & bắt đầu" (plays + records on ended).
          setAudioBlocked(true);
        });
    }
  }, [phase, question.audioUrl]);

  // ── Recording phase: start MediaRecorder ─────────────────────────────────
  useEffect(() => {
    if (phase !== 'recording') return;
    startRecording();
  }, [phase, startRecording]);

  // ── Audio event handlers ─────────────────────────────────────────────────
  // All transitions go through setPhase — the per-phase effects above pick
  // them up and drive MediaRecorder.

  const handleAudioEnded = useCallback(() => {
    setAudioReplaying(false);
    setPhase((p) => (p === 'intro' ? 'recording' : p));
  }, []);

  const handleAudioError = useCallback(() => {
    // Treat a 404 / decode error as "no audio" and proceed.
    setAudioReplaying(false);
    setPhase((p) => (p === 'intro' ? 'recording' : p));
  }, []);

  const handleManualPlay = useCallback(() => {
    const a = audioRef.current;
    if (!a) return;
    setAudioBlocked(false);
    a.play().catch(() => {
      // Even manual play failed — give up on TTS, just record.
      setPhase('recording');
    });
  }, []);

  const handleSkipTts = useCallback(() => {
    const a = audioRef.current;
    if (a) {
      a.pause();
      a.currentTime = 0;
    }
    setPhase('recording');
  }, []);

  /**
   * Toggle the question's TTS replay during recording. Behaves like a
   * play/pause control on the same button:
   *   - idle → start playback from the beginning, flip the flag.
   *   - playing → pause + rewind so the next click starts cleanly.
   *
   * Recording continues throughout — the wall-clock cap keeps counting
   * and the mic stays open, so a speaker setup will let the playback
   * bleed into the answer audio. Headphones recommended; question text
   * is on screen as a fallback either way.
   */
  const handleReplayAudio = useCallback(() => {
    const a = audioRef.current;
    if (!a || !question.audioUrl) return;
    if (audioReplaying) {
      a.pause();
      a.currentTime = 0;
      setAudioReplaying(false);
      return;
    }
    a.currentTime = 0;
    setAudioReplaying(true);
    a.play().catch(() => {
      // Browser refused to replay — drop the flag so the button can be
      // re-tried on the next user gesture.
      setAudioReplaying(false);
    });
  }, [audioReplaying, question.audioUrl]);

  // ── Render ────────────────────────────────────────────────────────────────

  if (phase === 'requesting') {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-8 text-center">
        <p className="text-sm text-on-surface-variant">Đang yêu cầu quyền truy cập camera & micro…</p>
      </div>
    );
  }

  if (phase === 'denied') {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6">
        <div className="flex items-start gap-3 mb-4">
          <VideoOff className="w-5 h-5 text-red-500 mt-0.5" />
          <div>
            <h3 className="text-sm font-bold text-on-surface mb-1">Không truy cập được camera / micro</h3>
            <p className="text-xs text-on-surface-variant leading-relaxed">
              {permissionError ?? 'Trình duyệt đã từ chối quyền. Hãy bật lại trong cài đặt site và thử lại.'}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={requestStream}
          className="w-full py-2.5 rounded-xl bg-secondary text-on-secondary font-semibold text-sm hover:bg-secondary-container transition-colors"
        >
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl overflow-hidden">
      <div className="relative aspect-video bg-black">
        <video
          ref={videoRef}
          autoPlay
          muted
          playsInline
          className="w-full h-full object-cover"
        />

        {phase === 'intro' && !audioBlocked && (
          <div className="absolute top-3 left-3 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-black/60 text-white text-xs font-bold">
            <Volume2 className="w-3 h-3" />
            Đang đọc câu hỏi…
          </div>
        )}

        {phase === 'recording' && (
          <div className="absolute top-3 left-3 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-red-500/90 text-white text-xs font-bold">
            <span className="w-2 h-2 rounded-full bg-white animate-pulse" />
            REC
          </div>
        )}

        <Countdown
          elapsed={elapsed}
          max={maxSeconds}
          active={phase === 'recording'}
        />
      </div>

      <div className="p-4">
        <MicMeter level={micLevel} muted={phase === 'submitting' || busy} />

        <div className="mt-4">
          {phase === 'intro' && audioBlocked && (
            <button
              type="button"
              onClick={handleManualPlay}
              className="w-full py-3 rounded-xl bg-secondary text-on-secondary font-semibold text-sm hover:bg-secondary-container transition-colors inline-flex items-center justify-center gap-2"
            >
              <Volume2 className="w-4 h-4" />
              Nghe câu hỏi & bắt đầu
            </button>
          )}

          {phase === 'intro' && !audioBlocked && (
            <button
              type="button"
              onClick={handleSkipTts}
              className="w-full py-3 rounded-xl bg-secondary text-on-secondary font-semibold text-sm hover:bg-secondary-container transition-colors inline-flex items-center justify-center gap-2"
            >
              <Camera className="w-4 h-4" />
              Bắt đầu trả lời ngay
            </button>
          )}

          {phase === 'recording' && (
            <>
              <button
                type="button"
                onClick={stopRecording}
                className="w-full py-3 rounded-xl bg-red-500 text-white font-semibold text-sm hover:bg-red-600 transition-colors inline-flex items-center justify-center gap-2"
              >
                <Square className="w-4 h-4 fill-current" />
                Kết thúc & nộp
              </button>
              {question.audioUrl && (
                <button
                  type="button"
                  onClick={handleReplayAudio}
                  className="w-full mt-2 py-2 rounded-xl border border-outline-variant text-on-surface-variant font-medium text-xs hover:bg-surface-container-low transition-colors inline-flex items-center justify-center gap-1.5"
                  title="Đeo tai nghe để tránh micro thu lại tiếng câu hỏi"
                >
                  {audioReplaying ? (
                    <>
                      <Pause className="w-3.5 h-3.5" />
                      Dừng phát lại
                    </>
                  ) : (
                    <>
                      <Volume2 className="w-3.5 h-3.5" />
                      Nghe lại câu hỏi
                    </>
                  )}
                </button>
              )}
            </>
          )}

          {phase === 'submitting' && (
            <button
              type="button"
              disabled
              className="w-full py-3 rounded-xl bg-secondary/40 text-on-secondary font-semibold text-sm cursor-not-allowed"
            >
              Đang gửi câu trả lời…
            </button>
          )}
        </div>
      </div>

      {/* Hidden audio element — volume + control owned by the recorder. */}
      <audio
        ref={audioRef}
        preload="auto"
        onEnded={handleAudioEnded}
        onError={handleAudioError}
        className="hidden"
      />
    </div>
  );
}

function Countdown({ elapsed, max, active }: { elapsed: number; max: number; active: boolean }) {
  const remaining = Math.max(0, max - elapsed);
  const mm = Math.floor(remaining / 60);
  const ss = remaining % 60;
  const warn = remaining <= 30;
  return (
    <div
      className={`absolute top-3 right-3 px-2.5 py-1 rounded-full font-mono text-xs font-bold ${
        active
          ? warn
            ? 'bg-red-500/90 text-white animate-pulse'
            : 'bg-black/60 text-white'
          : 'bg-black/40 text-white/80'
      }`}
    >
      {mm}:{ss.toString().padStart(2, '0')}
    </div>
  );
}

function MicMeter({ level, muted }: { level: number; muted: boolean }) {
  const segments = 12;
  const filled = Math.round(level * segments);
  return (
    <div className="flex items-center gap-2">
      {muted ? (
        <MicOff className="w-4 h-4 text-on-surface-variant" />
      ) : (
        <Mic className="w-4 h-4 text-on-surface-variant" />
      )}
      <div className="flex-1 flex items-center gap-0.5 h-2">
        {Array.from({ length: segments }).map((_, i) => (
          <span
            key={i}
            className={`flex-1 h-full rounded-sm transition-colors ${
              i < filled
                ? i < segments - 3
                  ? 'bg-emerald-500'
                  : i < segments - 1
                    ? 'bg-amber-500'
                    : 'bg-red-500'
                : 'bg-outline-variant/40'
            }`}
          />
        ))}
      </div>
    </div>
  );
}
