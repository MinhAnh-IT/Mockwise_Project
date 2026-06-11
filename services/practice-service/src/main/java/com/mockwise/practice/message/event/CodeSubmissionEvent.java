package com.mockwise.practice.message.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Payload published to the {@code code-submission} topic. Matches
 * judge-service's {@code SubmissionEvent} shape exactly, plus {@code origin}
 * ({@code "PRACTICE"}) so the verdict comes back tagged for this service. The
 * judge runs every case it is given; {@code is_hidden} is intentionally absent
 * here (practice tracks hidden ids on its own submission row).
 */
public record CodeSubmissionEvent(
        String submissionId,
        String origin,
        String language,
        String code,
        FunctionMeta functionMeta,
        List<TestCase> testCases
) {

    public record FunctionMeta(
            String fn,
            List<Param> params,
            @JsonProperty("return") String returnType,
            boolean orderMatters,
            boolean inPlace
    ) {}

    public record Param(String name, String type) {}

    public record TestCase(
            String id,
            Map<String, Object> inputData,
            Map<String, Object> expectedOutput
    ) {}
}
