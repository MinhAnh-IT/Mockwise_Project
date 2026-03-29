package com.mockwise.gateway.filter.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IntrospectData {
    private boolean active;
    private String userId;
    private String username; // email
    private String role;
    private long exp;
}
