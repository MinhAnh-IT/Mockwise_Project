package com.mockwise.interview.dto.request;

import com.mockwise.interview.enums.AnswerType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Input for {@code AnswerService.submit}. Type discriminates between
 * VIDEO (BEHAVIORAL / CORE_CONCEPTUAL spoken answer pointed at a storage
 * object) and CODE (LIVE_CODING inline source).
 *
 * <p>Validation rules:
 * <ul>
 *   <li>VIDEO → {@code storageObjectId} required, {@code code/language} ignored.</li>
 *   <li>CODE  → {@code code/language} required, {@code storageObjectId} ignored.</li>
 * </ul>
 *
 * <p>The conditional rules are checked in the service layer rather than
 * with a custom validator because the message text needs to reference
 * the {@code AnswerType} the caller actually sent.
 *
 * <p>Lever 2 (realtime-stt-plan.md §6.1/§6.2): VIDEO answers may carry a
 * real-time transcript the browser built while the candidate spoke. When
 * present (and the feature is enabled) the answer scores straight off it —
 * {@code storageObjectId} becomes optional and the video uploads in the
 * background, attached afterwards via {@code POST .../attach-video}. The
 * transcript comes straight from the client, so it is hard-capped here
 * (§8.3 #8) and never interpolated into the AI system prompt.
 */
public record SubmitAnswerInput(

        @NotNull(message = "type is required (VIDEO or CODE)")
        AnswerType type,

        UUID storageObjectId,

        @Size(max = 262144, message = "code must be at most 256KB")
        String code,

        @Size(max = 20, message = "language must be at most 20 characters")
        String language,

        // Real-time transcript (fast path). Null → legacy flow (wait for
        // batch STT). Capped hard because it bypasses server-side STT.
        @Size(max = 20000, message = "transcriptText must be at most 20000 characters")
        String transcriptText,

        // "vi" / "en" — the language the candidate spoke, as detected client
        // side. Profile language still wins for the feedback (see service).
        @Size(max = 16, message = "transcriptLanguage must be at most 16 characters")
        String transcriptLanguage,

        // Recording wall-clock in ms, from the recorder. Best-effort metadata
        // surfaced to the AI as durationSeconds.
        Integer transcriptDurationMs,

        // "REALTIME_WEBSPEECH" today. Recorded on the answer for audit.
        @Size(max = 32, message = "transcriptSource must be at most 32 characters")
        String transcriptSource
) {}
