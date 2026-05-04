package com.mockwise.interview.planner;

/**
 * What the planner returns: the decision the orchestrator should act on,
 * plus the diff of state mutations that decision implies.
 */
public record PlannerOutcome(
        PlannerDecision decision,
        PlannerSideEffects sideEffects
) {}
