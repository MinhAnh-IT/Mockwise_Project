package com.mockwise.practice.client.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Mirrors question-bank's {@code CodingQuestionResponse} — the FULL detail
 * including hidden test cases. practice-service strips hidden cases before
 * anything reaches a user; the full set is used only to build a Submit run.
 * Unknown fields (type/status/createdBy/...) are ignored by Jackson.
 */
public record QbCodingDetail(
        String id,
        String difficulty,
        List<String> tags,
        String title,
        String description,
        String constraints,
        String optimalTimeComplexity,
        String optimalSpaceComplexity,
        QbFunctionMeta functionMeta,
        Map<String, String> starterCode,
        List<QbTestCase> testCases
) {

    public record QbFunctionMeta(
            String fn,
            List<QbParam> params,
            @JsonProperty("return") String returnType,
            boolean orderMatters,
            boolean inPlace
    ) {}

    public record QbParam(String name, String type) {}

    public record QbTestCase(
            String id,
            Map<String, Object> inputData,
            Map<String, Object> expectedOutput,
            @JsonProperty("is_hidden") boolean hidden,
            String note
    ) {}
}
