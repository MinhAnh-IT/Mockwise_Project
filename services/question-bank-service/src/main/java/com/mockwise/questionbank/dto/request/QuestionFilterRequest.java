package com.mockwise.questionbank.dto.request;

import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Selection-time filter used by interview-service to fetch a candidate
 * pool for the next question. Matches the contract documented in
 * services/interview-service/docs/question-selection-design.md §10.
 *
 * <p>Only BEHAVIORAL and CORE_CONCEPTUAL types are supported here —
 * coding questions follow a separate flow via the existing endpoints.
 */
public record QuestionFilterRequest(

        @NotNull(message = "type is required")
        QuestionType type,

        /** Required when type=BEHAVIORAL. Must be a {@code Competency} enum name. */
        String competency,

        /** Required when type=CORE_CONCEPTUAL. Must be a {@code Domain} enum name. */
        String domain,

        /** Optional. CORE only — restricts to questions targeting this role. */
        String targetRole,

        Difficulty difficulty,

        /** Free-form tag overlap filter (matches if any tag is present in the question). */
        List<String> tagsAny,

        /** Question IDs to exclude (already-asked, recently-strong, etc.). */
        List<String> excludeIds,

        /** When true, restricts to questions flagged as session openers. */
        boolean requireOpener,

        @Min(value = 1, message = "limit must be >= 1")
        @Max(value = 50, message = "limit must be <= 50")
        Integer limit
) {
    public int effectiveLimit() {
        return limit != null ? limit : 10;
    }
}
