package com.mockwise.interview.service;

import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.repository.InterviewSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sweeps IN_PROGRESS sessions whose time budget has run out and finalizes
 * them, so a session whose user closed the tab (and therefore never submits
 * again to trip {@link SessionService#ensureWithinTimeBudget}) still ends and
 * gets scored.
 *
 * <p>The in-flight guard on answer submit is the fast path — this reaper is
 * the safety net for abandoned sessions. Each expiry runs in its own
 * transaction via {@link SessionService#expireIfOverBudget(java.util.UUID)}
 * so one stuck row can't block the rest of the sweep.
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
    SessionService sessionService;

    // 60s is well within the resolution a candidate would notice — the FE
    // countdown ends the session client-side at the same deadline; this only
    // has to catch the closed-tab case before the report is opened.
    @Scheduled(fixedDelayString = "${interview.session-deadline.reaper-interval-ms:60000}")
    public void sweep() {
        List<InterviewSession> inProgress = sessionRepo.findByStatus(SessionStatus.IN_PROGRESS);
        if (inProgress.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        int expired = 0;
        for (InterviewSession s : inProgress) {
            OffsetDateTime deadline = SessionService.deadlineOf(s);
            if (deadline == null || !now.isAfter(deadline)) {
                continue;
            }
            try {
                if (sessionService.expireIfOverBudget(s.getId())) {
                    expired++;
                }
            } catch (Exception ex) {
                // Don't let one bad row abort the sweep — the next tick retries.
                log.warn("Deadline reaper: failed to expire session {}: {}",
                        s.getId(), ex.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Deadline reaper: finalized {} over-budget session(s)", expired);
        }
    }
}
