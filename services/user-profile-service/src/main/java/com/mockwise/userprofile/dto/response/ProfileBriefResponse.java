package com.mockwise.userprofile.dto.response;

/**
 * Minimal profile projection for service-to-service batch lookups (e.g. the
 * practice leaderboard enriching a page of userIds with display names). Avatars
 * are intentionally omitted — the avatar URL is caller-scoped ({@code /avatars/me})
 * and cannot represent another user.
 */
public record ProfileBriefResponse(String userId, String fullName) {}
