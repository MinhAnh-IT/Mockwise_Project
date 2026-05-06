package com.mockwise.interview.client.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionFilterResponse(
        List<QuestionCandidate> candidates,
        int total
) {}
