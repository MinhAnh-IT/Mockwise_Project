package com.mockwise.interview.service;

import com.mockwise.interview.repository.InterviewSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sweeps IN_PROGRESS sessions a candidate walked away from — closed the app
 * without pressing "end interview" — and gives them a terminal status, so
 * they don't sit IN_PROGRESS for the rest of their time budget.
 *
 * <p><b>Why a second reaper.</b> {@link SessionDeadlineReaper} only closes a
 * session at its hard wall-clock end ({@code started_at + time_budget_minutes}).
 * Blueprints with a long budget (60–90 min) leave an abandoned session
 * IN_PROGRESS for up to that long. This reaper closes on <em>inactivity</em>
 * instead: {@code updated_at} is bumped by the planner on every scored answer
 * (via {@link SideEffectApplier}) and by {@link AnswerService}, so an
 * {@code updated_at} older than {@link #idleThresholdMinutes} means nothing
 * has happened on the session for that long — the candidate is gone.
 *
 * <p><b>Two terminal states</b> (per product decision):
 * <ul>
 *   <li><b>Answered</b> (≥1 answer row) → COMPLETED, then the overall-review
 *       gate scores whatever was submitted — same treatment as a timeout.</li>
 *   <li><b>Unanswered</b> (no answer at all) → CANCELLED — the candidate never
 *       engaged, so no AI report is generated.</li>
 * </ul>
 *
 * <p><b>Resilience contract</b> mirrors {@link SessionDeadlineReaper}:
 * discovery and finalization run as native, enum-free SQL
 * ({@link InterviewSessionRepository#findIdleAnsweredInProgressIds(OffsetDateTime)}
 * and the bulk finalizers), so a single legacy row with a retired
 * {@code interview_type} can't throw at result-set mapping and abort the whole
 * sweep. The post-finalize overall-review gate is best-effort and isolated
 * per-session.
 *
 * <p>{@code @EnableScheduling} is already on the application class.
 */
@Slf4j
@Component
public class SessionIdleReaper {

    private final InterviewSessionRepository sessionRepo;
    private final SessionFinalizerService sessionFinalizer;

    // Field injection (non-final) on purpose: mixing @Value with Lombok's
    // @RequiredArgsConstructor + makeFinal fields is the runtime gotcha that
    // leaves the constructor param unbound. Keep the two collaborators as
    // explicit final ctor deps and let @Value populate this after construction.
    @Value("${interview.session-idle.threshold-minutes:60}")
    private int idleThresholdMinutes;

    public SessionIdleReaper(InterviewSessionRepository sessionRepo,
                             SessionFinalizerService sessionFinalizer) {
        this.sessionRepo = sessionRepo;
        this.sessionFinalizer = sessionFinalizer;
    }

    // Runs every 5 min by default — a 1-hour idle threshold doesn't need a
    // tighter cadence, and the discovery query is cheap (status + updated_at).
    @Scheduled(fixedDelayString = "${interview.session-idle.reaper-interval-ms:300000}")
    public void sweep() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(idleThresholdMinutes);

        // Capture answered-session ids before the flip so we can run the
        // overall-review gate on each. Native + scalar so a legacy unmappable
        // interview_type can't abort the sweep.
        List<String> answeredIds = sessionRepo.findIdleAnsweredInProgressIds(cutoff);

        // Answered idle sessions → COMPLETED (one statement, legacy rows
        // included), then the gate below scores what was submitted.
        int completed = sessionRepo.completeIdleAnsweredInProgress(cutoff);
        // Unanswered idle sessions → CANCELLED. Mutually exclusive with the
        // above via NOT EXISTS, and no overall-review follow-up is needed.
        int cancelled = sessionRepo.cancelIdleUnansweredInProgress(cutoff);

        if (completed == 0 && cancelled == 0) {
            return;
        }

        // Best-effort overall-review staging for the just-completed sessions.
        // No-op when an answer is still mid-flight (the evaluation consumer
        // fires it later); per-id try/catch so an unmappable row is logged
        // once and skipped — it is already COMPLETED, so the candidate is
        // unblocked and the next sweep no longer sees it.
        int gated = 0;
        for (String idStr : answeredIds) {
            try {
                sessionFinalizer.maybeRequestOverallReview(UUID.fromString(idStr));
                gated++;
            } catch (Exception ex) {
                log.warn("Idle reaper: overall-review gate skipped for session {}: {}",
                        idStr, ex.getMessage());
            }
        }

        log.info("Idle reaper: completed {} answered + cancelled {} unanswered "
                + "idle session(s) (idle > {} min), gated {}",
                completed, cancelled, idleThresholdMinutes, gated);
    }
}
