/**
 * Lever 2 (realtime-stt-plan.md §5 Provider C). Builds a transcript while the
 * candidate speaks by streaming audio to **ElevenLabs Scribe v2 Realtime** over
 * a WebSocket, so a VIDEO answer can be scored at submit time without waiting
 * for the upload + batch STT.
 *
 * <p>Why ElevenLabs (not the browser Web Speech API): it's the same Scribe
 * family already trusted for Vietnamese batch STT, and it accepts manually
 * chunked PCM — so we feed it from the SAME `getUserMedia` stream the recorder
 * uses (one mic, teed via an AudioContext). That sidesteps the Web-Speech
 * two-captures-on-one-mic risk entirely, and works on any browser with
 * WebSocket + AudioContext (no Safari/Firefox gap).
 *
 * <p>The API key never reaches the browser: interview-service mints a single-use
 * token (`getToken`) which authenticates the WS connection. A failure anywhere
 * (token, WS, audio) just yields an empty transcript → the caller falls back to
 * the legacy upload-then-batch-STT flow. Batch STT from the video remains the
 * authoritative backstop regardless.
 */
export type RealtimeTranscript = { text: string; durationMs: number };

export type RealtimeTranscriber = {
  /** Stop capture, flush the tail, resolve the accumulated committed transcript. */
  stop: () => Promise<RealtimeTranscript>;
  /** Tear down without producing a transcript (question swap / unmount). */
  abort: () => void;
};

export type RealtimeToken = { token: string; model: string; expiresInSeconds?: number };

const WS_BASE =
  (import.meta.env.VITE_ELEVENLABS_STT_WS_URL as string | undefined) ??
  'wss://api.elevenlabs.io/v1/speech-to-text/realtime';

// ElevenLabs realtime accepts pcm_16000; we resample to 16 kHz mono via an
// AudioContext opened at that rate.
const TARGET_SAMPLE_RATE = 16000;

// How long to wait after stop() for the final committed_transcript to arrive
// before closing. Orders of magnitude under the upload it replaces.
const FLUSH_WINDOW_MS = 900;

/** Minimum word count for a real-time transcript to be trusted for scoring (§8.1 #1). */
export const MIN_TRANSCRIPT_WORDS = 10;

export function wordCount(text: string): number {
  const t = text.trim();
  return t === '' ? 0 : t.split(/\s+/).length;
}

type AudioCtxCtor = typeof AudioContext;

function audioContextCtor(): AudioCtxCtor | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as { AudioContext?: AudioCtxCtor; webkitAudioContext?: AudioCtxCtor };
  return w.AudioContext ?? w.webkitAudioContext ?? null;
}

/**
 * True when the browser can stream audio (WebSocket + AudioContext) and the
 * Lever 2 flag isn't explicitly disabled. Default ON: a build without the env
 * var still enables the fast path (so a fresh deploy needs no setup); set
 * VITE_REALTIME_STT_ENABLED=false at build time to force the legacy flow. The
 * backend mints a token only when its own flag is on, so a mismatch (or an
 * ElevenLabs account without realtime) degrades safely to legacy.
 */
export function isRealtimeSttEnabled(): boolean {
  return (
    import.meta.env.VITE_REALTIME_STT_ENABLED !== 'false' &&
    typeof window !== 'undefined' &&
    typeof window.WebSocket !== 'undefined' &&
    audioContextCtor() !== null
  );
}

// ── Pure helpers (unit-tested; the WS/audio wiring below is integration) ─────

/** Clamp Float32 PCM [-1,1] to signed 16-bit. */
export function floatTo16BitPCM(input: Float32Array): Int16Array {
  const out = new Int16Array(input.length);
  for (let i = 0; i < input.length; i++) {
    const s = Math.max(-1, Math.min(1, input[i]));
    out[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return out;
}

/** Base64-encode raw little-endian PCM16 bytes for the `audio_base_64` field. */
export function int16ToBase64(int16: Int16Array): string {
  const bytes = new Uint8Array(int16.buffer, int16.byteOffset, int16.byteLength);
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

type ScribeMessage = { message_type?: string; text?: string };

/**
 * Folds one ElevenLabs WS message into the accumulated transcript. Only
 * `committed_transcript[_with_timestamps]` count — partials are revised in
 * place and would double-count if accumulated (cf. Web Speech isFinal-only).
 */
export function appendCommitted(prev: string, msg: ScribeMessage): string {
  if (!msg || typeof msg.text !== 'string') return prev;
  if (
    msg.message_type === 'committed_transcript' ||
    msg.message_type === 'committed_transcript_with_timestamps'
  ) {
    const t = msg.text.trim();
    if (!t) return prev;
    return prev ? `${prev} ${t}` : t;
  }
  return prev;
}

// ── The transcriber ──────────────────────────────────────────────────────────

export function createRealtimeTranscriber(opts: {
  language: string;
  stream: MediaStream;
  getToken: () => Promise<RealtimeToken>;
}): RealtimeTranscriber {
  const startedAt = Date.now();
  let finalText = '';
  let stopped = false;
  let ws: WebSocket | null = null;
  let audioCtx: AudioContext | null = null;
  let source: MediaStreamAudioSourceNode | null = null;
  let processor: ScriptProcessorNode | null = null;

  const snapshot = (): RealtimeTranscript => ({
    text: finalText.trim(),
    durationMs: Date.now() - startedAt,
  });

  const teardownAudio = () => {
    try {
      processor?.disconnect();
    } catch {
      /* noop */
    }
    try {
      source?.disconnect();
    } catch {
      /* noop */
    }
    try {
      void audioCtx?.close();
    } catch {
      /* noop */
    }
    processor = null;
    source = null;
    audioCtx = null;
  };

  // Async init — token → WS → audio pipeline. Any failure leaves finalText
  // empty so the caller falls back to legacy (never throws into the recorder).
  void (async () => {
    try {
      const { token, model } = await opts.getToken();
      if (stopped) return;

      const lang = opts.language.toLowerCase().startsWith('vi') ? 'vi' : 'en';
      const url =
        `${WS_BASE}?model_id=${encodeURIComponent(model)}` +
        `&language_code=${lang}` +
        `&audio_format=pcm_${TARGET_SAMPLE_RATE}` +
        `&commit_strategy=vad` +
        `&token=${encodeURIComponent(token)}`;

      ws = new WebSocket(url);
      ws.onmessage = (ev) => {
        if (typeof ev.data !== 'string') return;
        try {
          finalText = appendCommitted(finalText, JSON.parse(ev.data) as ScribeMessage);
        } catch {
          /* ignore non-JSON frames */
        }
      };
      ws.onerror = () => {
        /* errors surface as an empty transcript → legacy fallback */
      };

      const Ctor = audioContextCtor();
      if (!Ctor || stopped) return;
      audioCtx = new Ctor({ sampleRate: TARGET_SAMPLE_RATE });
      // Autoplay policy can leave a fresh context 'suspended' → onaudioprocess
      // never fires and no audio is sent. Recording starts after a user gesture
      // so resume() succeeds; if it doesn't, the empty transcript falls back.
      if (audioCtx.state === 'suspended') {
        await audioCtx.resume().catch(() => {});
        if (stopped) return;
      }
      source = audioCtx.createMediaStreamSource(opts.stream);
      // ScriptProcessor is deprecated but universally available and adequate
      // here; an AudioWorklet would avoid the main-thread hop if this ever
      // shows up in profiling.
      processor = audioCtx.createScriptProcessor(4096, 1, 1);
      processor.onaudioprocess = (e) => {
        if (stopped || !ws || ws.readyState !== WebSocket.OPEN) return;
        const pcm = floatTo16BitPCM(e.inputBuffer.getChannelData(0));
        ws.send(
          JSON.stringify({
            message_type: 'input_audio_chunk',
            audio_base_64: int16ToBase64(pcm),
            sample_rate: TARGET_SAMPLE_RATE,
          }),
        );
      };
      source.connect(processor);
      processor.connect(audioCtx.destination);
    } catch (e) {
      console.warn('[realtime-stt] init failed — will fall back to batch STT:', e);
    }
  })();

  return {
    stop() {
      stopped = true;
      return new Promise<RealtimeTranscript>((resolve) => {
        const finish = () => {
          teardownAudio();
          try {
            ws?.close();
          } catch {
            /* noop */
          }
          ws = null;
          resolve(snapshot());
        };
        // Force-commit the tail so the last words are finalized, then wait a
        // short window for the committed_transcript before closing.
        try {
          if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(
              JSON.stringify({
                message_type: 'input_audio_chunk',
                audio_base_64: '',
                commit: true,
                sample_rate: TARGET_SAMPLE_RATE,
              }),
            );
          }
        } catch {
          /* noop */
        }
        setTimeout(finish, FLUSH_WINDOW_MS);
      });
    },
    abort() {
      stopped = true;
      teardownAudio();
      try {
        ws?.close();
      } catch {
        /* noop */
      }
      ws = null;
    },
  };
}
