package com.mockwise.interview.dto.assessment;

import com.mockwise.interview.enums.Completeness;
import com.mockwise.interview.enums.Correctness;
import com.mockwise.interview.enums.Depth;
import com.mockwise.interview.enums.Grade;
import com.mockwise.interview.enums.HireSignal;
import com.mockwise.interview.enums.SignalStrength;

import java.util.List;

/**
 * The canonical, type-agnostic verdict the planner reads. Derived from
 * whichever AI output shape (Behavioral / Conceptual / LiveCoding) the
 * answer produced, by {@code AssessmentVerdictMapper}.
 *
 * <p>Persisted into {@code answer.verdict} as JSONB. The raw AI output
 * goes into {@code answer.raw_evaluation} for audit / replay.
 *
 * <p>The shape is deliberately flat so the planner's decision tree
 * (question-selection-design.md §5) can switch on these fields directly
 * without traversing nested AI output.
 */
public record AssessmentVerdict(
        /* 0.0–10.0; AI returns 0–100 — divide by 10 for human-friendly scale */
        Float scoreNormalized,
        HireSignal hireSignal,
        Grade grade,
        SignalStrength signalStrength,
        Completeness completeness,
        Correctness correctness,
        Depth depth,
        List<WeakTarget> weakTargets,
        List<StrongTarget> strongTargets
) {}
