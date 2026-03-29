package com.mockwise.iam.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenIntrospectResponse(
        boolean active,
        String userId,
        String username,
        String role,
        long exp
) {}
