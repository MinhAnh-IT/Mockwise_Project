package com.mockwise.interview.client.userprofile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionResponse(
        String positionId,
        String trackId,
        String trackName,
        String levelId,
        String levelName
) {}
