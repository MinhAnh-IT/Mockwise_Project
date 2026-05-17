package com.mockwise.interview.service;

import com.mockwise.interview.repository.InterviewSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Sweeps IN_PROGRESS sessions whose time budget has run out and finalizes
 * them, so a session whose user closed the tab — or whose judge/AI verdict
 * never lands (the last coding answer's
 * {@link SessionFinalizerService#maybeRequestOverallReview} gate never
 * fires) — still ends and lets the FE leave the "waiting for next question"
 * screen. The in-flight guard on answer submit
 * ({@link SessionService#ensureWithinTimeBudget}) is the fast path; this
 * reaper is the safety net for everything that never submits again.
 *
 * <p><b>Resilience contract:</b> discovery and finalization run as native,
 * enum-free SQL ({@link InterviewSessionRepository#findOverBudgetInProgressIds()}
 * / {@link InterviewSessionRepository#completeOverBudgetInProgressSessions()}).
 * Hydrating the {@code InterviewSession} entity here would let a single
 * legacy row with a retired {@code interview_type} (e.g. {@code 'MIXED'})
 * throw at result-set mapping time and abort the whole sweep — which is
 * exactly what previously stranded coding sessions IN_PROGRESS forever.
 * The post-finalize overall-review gate is best-effort and isolated
 * per-session so an unmappable row is logged once and skipped instead of
 * poisoning the batch.
 *
 * <p>Mirrors {@code OutboxPoller}'s {@code @Scheduled} style;
 * {@code @EnableScheduling} is already on the application class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SessionDeadlineReaper {

    InterviewSessionRepository sessionRepo;
    SessionFinalizerService sessionFinalizer;

    // 60s is well within the resolution a candidate would notice — the FE
    // countdown ends the session client-side at the same deadline; this only
    // has to catch the closed-tab / stuck-judge case before the report opens.
    @Scheduled(fixedDelayString = "${interview.session-deadline.reaper-interval-ms:60000}")
    public void sweep() {
        // Capture the over-budget ids before flipping them — we still want
        // to run the overall-review gate on each one. Native + scalar so a
        // legacy unmappable interview_type can't abort the sweep.
        List<String> overBudgetIds = sessionRepo.findOverBudgetInProgressIds();
        if (overBudgetIds.isEmpty()) {
            return;
        }

        // One statement flips them all (legacy unmappable rows included) to
        // COMPLETED, so the FE's poll immediately sees a terminal status and
        // navigates to the report. Its own transaction (on the repo method).
        int completed = sessionRepo.completeOverBudgetInProgressSessions();

        // Best-effort: stage the AI overall-review for each just-finalized
        // session (no-op when answers are still mid-flight — the
        // evaluation-completed consumer fires it later). Per-id + try/catch
        // so a row whose interview_type can't be mapped is logged once and
        // skipped; it is already COMPLETED, so the candidate is unblocked
        // regardless, and the next sweep no longer sees it.
        int gated = 0;
        for (String idStr : overBudgetIds) {
            try {
                sessionFinalizer.maybeRequestOverallReview(UUID.fromString(idStr));
                gated++;
            } catch (Exception ex) {
                log.warn("Deadline reaper: overall-review gate skipped for session {}: {}",
                        idStr, ex.getMessage());
            }
        }

        log.info("Deadline reaper: finalized {} over-budget session(s), gated {}",
                completed, gated);
    }
}
