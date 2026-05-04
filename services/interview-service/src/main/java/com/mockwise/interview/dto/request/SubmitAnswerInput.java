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
 */
public record SubmitAnswerInput(

        @NotNull(message = "type is required (VIDEO or CODE)")
        AnswerType type,

        UUID storageObjectId,

        @Size(max = 262144, message = "code must be at most 256KB")
        String code,

        @Size(max = 20, message = "language must be at most 20 characters")
        String language
) {}
