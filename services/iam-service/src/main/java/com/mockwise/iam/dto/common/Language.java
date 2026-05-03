package com.mockwise.iam.dto.common;

/**
 * Mirror of {@code com.mockwise.userprofile.entity.Language} kept enum-name
 * compatible so Jackson serializes/deserializes across the Feign boundary
 * without a custom converter.
 */
public enum Language {
    VI,
    EN
}
