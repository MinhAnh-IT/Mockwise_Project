package com.mockwise.practice.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Practice tunables bound from {@code practice.*}. {@link #languages()} is the
 * whitelist of languages judge-service can run; {@link Run#maxCodeBytes()} caps
 * source size; {@link Watchdog} parameters drive the stuck-submission sweep.
 */
@ConfigurationProperties(prefix = "practice")
public record PracticeProperties(
        Run run,
        List<String> languages,
        Watchdog watchdog
) {

    public record Run(int maxCodeBytes) {}

    public record Watchdog(long timeoutSeconds, long intervalMs) {}

    /** Case-insensitive whitelist lookup. */
    public Set<String> languageSet() {
        return languages == null
                ? Set.of()
                : languages.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }
}
