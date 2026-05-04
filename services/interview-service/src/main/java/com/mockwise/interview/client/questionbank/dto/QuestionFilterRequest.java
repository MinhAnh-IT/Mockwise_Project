package com.mockwise.interview.client.questionbank.dto;

import com.mockwise.interview.entity.enums.Difficulty;
import com.mockwise.interview.entity.enums.QuestionType;

import java.util.List;

/**
 * Mirror of {@code questionbank.dto.request.QuestionFilterRequest}.
 *
 * <p>Wire-compatible (field names + enum constants identical) so JSON
 * round-trips through Feign without per-field {@code @JsonProperty}
 * annotations. Defined locally rather than imported to keep our deploy
 * lifecycle independent of question-bank's.
 */
public record QuestionFilterRequest(
        QuestionType type,
        String competency,
        String domain,
        String targetRole,
        Difficulty difficulty,
        List<String> tagsAny,
        List<String> excludeIds,
        boolean requireOpener,
        Integer limit
) {}
