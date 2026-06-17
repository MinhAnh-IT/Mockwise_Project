import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  appendCommitted,
  floatTo16BitPCM,
  int16ToBase64,
  isRealtimeSttEnabled,
  MIN_TRANSCRIPT_WORDS,
  wordCount,
} from './realtimeTranscriber';

type W = {
  WebSocket?: unknown;
  AudioContext?: unknown;
  webkitAudioContext?: unknown;
};

beforeEach(() => {
  // jsdom ships neither WebSocket nor AudioContext — stub them so the
  // capability check can pass when we want to exercise the enabled path.
  (window as unknown as W).WebSocket = class {};
  (window as unknown as W).AudioContext = class {};
});

afterEach(() => {
  vi.unstubAllEnvs();
  delete (window as unknown as W).WebSocket;
  delete (window as unknown as W).AudioContext;
  delete (window as unknown as W).webkitAudioContext;
});

describe('wordCount + MIN gate (§8.1 #1 fallback)', () => {
  it('counts words; empty/short is below the trust threshold', () => {
    expect(wordCount('')).toBe(0);
    expect(wordCount('   ')).toBe(0);
    expect(wordCount('one two three')).toBe(3);
    expect(wordCount('one two three four five') < MIN_TRANSCRIPT_WORDS).toBe(true);
    expect(
      wordCount('one two three four five six seven eight nine ten eleven') >= MIN_TRANSCRIPT_WORDS,
    ).toBe(true);
  });
});

describe('isRealtimeSttEnabled', () => {
  it('default ON: enabled unless flag is explicitly "false"', () => {
    vi.stubEnv('VITE_REALTIME_STT_ENABLED', 'false');
    expect(isRealtimeSttEnabled()).toBe(false);

    vi.stubEnv('VITE_REALTIME_STT_ENABLED', 'true');
    expect(isRealtimeSttEnabled()).toBe(true);

    // Unset build var (fresh deploy, no setup) → still enabled.
    vi.stubEnv('VITE_REALTIME_STT_ENABLED', '');
    expect(isRealtimeSttEnabled()).toBe(true);
  });

  it('false when the browser cannot stream audio regardless of the flag', () => {
    vi.stubEnv('VITE_REALTIME_STT_ENABLED', 'true');
    delete (window as unknown as W).AudioContext;
    expect(isRealtimeSttEnabled()).toBe(false);
  });
});

describe('floatTo16BitPCM', () => {
  it('scales and clamps Float32 [-1,1] to signed 16-bit', () => {
    const out = floatTo16BitPCM(new Float32Array([0, 1, -1, 2, -2]));
    expect(out[0]).toBe(0);
    expect(out[1]).toBe(32767); // +1 → max
    expect(out[2]).toBe(-32768); // -1 → min
    expect(out[3]).toBe(32767); // clamp > 1
    expect(out[4]).toBe(-32768); // clamp < -1
  });
});

describe('int16ToBase64', () => {
  it('round-trips little-endian PCM16 bytes through base64', () => {
    const pcm = new Int16Array([0, 1, -1]);
    const b64 = int16ToBase64(pcm);
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    // little-endian: 0 → 00 00, 1 → 01 00, -1 → ff ff
    expect(Array.from(bytes)).toEqual([0, 0, 1, 0, 0xff, 0xff]);
  });
});

describe('appendCommitted (committed-only accumulation, §12.2 isFinal analogue)', () => {
  it('accumulates committed transcripts with a space; ignores partials/others', () => {
    let t = '';
    t = appendCommitted(t, { message_type: 'session_started', text: undefined });
    t = appendCommitted(t, { message_type: 'partial_transcript', text: 'interim guess' });
    t = appendCommitted(t, { message_type: 'committed_transcript', text: 'first part' });
    t = appendCommitted(t, { message_type: 'partial_transcript', text: 'more interim' });
    t = appendCommitted(t, {
      message_type: 'committed_transcript_with_timestamps',
      text: 'second part',
    });
    t = appendCommitted(t, { message_type: 'committed_transcript', text: '   ' }); // blank ignored
    expect(t).toBe('first part second part');
  });

  it('is a no-op on malformed messages', () => {
    expect(appendCommitted('keep', {} as never)).toBe('keep');
    expect(appendCommitted('keep', { message_type: 'committed_transcript' })).toBe('keep');
  });
});
