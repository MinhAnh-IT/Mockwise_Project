package com.mockwise.practice.client.userprofile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Minimal display info for one user — backs leaderboard name rendering. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileBrief(String userId, String fullName) {}
