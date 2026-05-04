package com.mockwise.interview.dto.assessment;

import com.mockwise.interview.enums.Severity;
import com.mockwise.interview.enums.WeakTargetKind;

/**
 * A single specific gap to probe in a follow-up question.
 *
 * <p>The planner sorts these by severity DESC and picks targets[0] when
 * driving Case C. The pair (kind, value) is what gets sent both to
 * {@code question_follow_up.probes_target_*} for the pre-authored lookup
 * and to {@code POST /follow-up/generate} for the AI-generated fallback.
 */
public record WeakTarget(
        WeakTargetKind kind,
        String value,
        Severity severity
) {}
