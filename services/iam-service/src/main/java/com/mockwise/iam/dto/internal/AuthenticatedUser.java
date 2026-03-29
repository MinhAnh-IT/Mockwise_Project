package com.mockwise.iam.dto.internal;

import com.mockwise.iam.enums.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticatedUser {
    String userId;
    String username;
    Role role;
    int tokenVersion;
}