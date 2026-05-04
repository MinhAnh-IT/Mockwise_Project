package com.mockwise.interview.client.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.QuestionType;

import java.util.List;

/**
 * One row in a candidate pool returned by {@code POST /questions/filter}.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} so question-bank can
 * keep adding fields without forcing a coordinated deploy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionCandidate(
        String id,
        QuestionType type,
        Difficulty difficulty,
        String text,
        String competency,
        String domain,
        List<String> tags,
        String audioKey,
        boolean isOpener,
        long askCount
) {}
