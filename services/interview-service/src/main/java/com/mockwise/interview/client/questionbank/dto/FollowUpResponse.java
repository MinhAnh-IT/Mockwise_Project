package com.mockwise.interview.client.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FollowUpResponse(
        String id,
        String parentQuestionId,
        String probesTargetKind,
        String probesTargetValue,
        String text,
        List<String> expectedPoints,
        String audioKey
) {}
