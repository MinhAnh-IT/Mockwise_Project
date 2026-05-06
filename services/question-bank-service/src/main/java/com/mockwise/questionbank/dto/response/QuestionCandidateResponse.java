package com.mockwise.questionbank.dto.response;

import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.QuestionType;

import java.util.List;

/**
 * Single row in a candidate pool. Carries enough metadata for
 * interview-service to apply post-fetch scoring (opener bonus, tag
 * overlap with user tech stack / industries) without an extra round-trip.
 */
public record QuestionCandidateResponse(
        String id,
        QuestionType type,
        Difficulty difficulty,
        String text,
        String competency,         // BEHAVIORAL only
        String domain,             // CORE only
        List<String> tags,
        String audioKey,
        boolean isOpener,
        long askCount
) {}
