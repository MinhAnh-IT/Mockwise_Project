package com.mockwise.practice.dto.response;

import com.mockwise.practice.enums.ProblemStatus;

import java.util.List;
import java.util.Map;

/**
 * The coding problem as the practice workspace needs it. Built from the
 * question-bank detail with hidden test cases dropped — {@link #sampleTestCases}
 * carries visible cases only, with their expected output and optional note.
 *
 * <p>Wire shape mirrors {@code frontend/src/types/coding.ts} so the existing
 * coding workspace renders it unchanged. The function return type is emitted as
 * {@code returnType} (question-bank stores it under the JSON key {@code return}).
 */
public record CodingProblemView(
        String id,
        String title,
        String description,
        String constraints,
        String optimalTimeComplexity,
        String optimalSpaceComplexity,
        FunctionMeta functionMeta,
        Map<String, String> starterCode,
        List<SampleTestCase> sampleTestCases,
        /** The current user's relationship to this problem: NONE / ATTEMPTED / SOLVED. */
        ProblemStatus myStatus
) {

    public record Param(String name, String type) {}

    public record FunctionMeta(
            String fn,
            List<Param> params,
            String returnType,
            boolean orderMatters,
            boolean inPlace
    ) {}

    public record SampleTestCase(
            String id,
            Map<String, Object> inputData,
            Map<String, Object> expectedOutput,
            String note
    ) {}
}
