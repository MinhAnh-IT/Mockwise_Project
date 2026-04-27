package com.mockwise.ttsstt.common.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum StatusCode {

    // --- TTS validation ---
    TTS_TEXT_TOO_LONG(4001, "Text length exceeds maximum (%d chars)", 400),
    TTS_VOICE_INVALID(4002, "Voice id not found", 400),

    // --- STT validation ---
    STT_OBJECT_NOT_AVAILABLE(4003, "Storage object not found or not READY", 400),
    STT_AUDIO_TOO_LONG(4004, "Audio duration exceeds maximum (%d seconds)", 400),
    STT_AUDIO_EXTRACT_FAILED(4005, "Audio extraction failed (corrupted video)", 400),

    // --- Auth ---
    INTERNAL_AUTH_FAILED(4011, "Missing or invalid internal API key", 401),

    // --- Resource lookup ---
    RESOURCE_NOT_FOUND(4041, "Job / transcript not found", 404),

    // --- Rate limit ---
    RATE_LIMITED(4291, "Rate limit exceeded", 429),

    // --- Upstream / infra ---
    ELEVENLABS_TTS_ERROR(5001, "ElevenLabs TTS upstream error: %s", 502),
    ELEVENLABS_TTS_TIMEOUT(5002, "ElevenLabs TTS timeout", 504),
    ELEVENLABS_STT_ERROR(5003, "ElevenLabs Scribe error: %s", 502),
    FFMPEG_FAILURE(5004, "ffmpeg failure", 500),
    STORAGE_UNREACHABLE(5005, "Storage service unreachable", 502),
    ELEVENLABS_PAYMENT_REQUIRED(5006, "ElevenLabs payment required: %s", 402);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
