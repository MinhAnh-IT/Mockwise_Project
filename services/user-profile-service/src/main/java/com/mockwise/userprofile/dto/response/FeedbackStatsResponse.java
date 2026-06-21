package com.mockwise.userprofile.dto.response;

public record FeedbackStatsResponse(
        long total,
        long newCount,
        long reviewedCount,
        long resolvedCount,
        long bugCount,
        long featureCount,
        long generalCount,
        double avgRating
) { }
