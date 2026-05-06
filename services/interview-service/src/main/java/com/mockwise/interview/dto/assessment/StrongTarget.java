package com.mockwise.interview.dto.assessment;

import com.mockwise.interview.enums.StrongTargetKind;

/**
 * A signal/concept the candidate already demonstrated. Forwarded to the
 * AI follow-up generator so it does not waste a probe re-asking what the
 * candidate has already shown.
 */
public record StrongTarget(
        StrongTargetKind kind,
        String value
) {}
