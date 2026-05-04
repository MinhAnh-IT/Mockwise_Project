package com.mockwise.interview.dto.request;

import com.mockwise.interview.enums.AnswerType;

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
 */
public record SubmitAnswerInput(
        AnswerType type,
        UUID storageObjectId,
        String code,
        String language
) {}
