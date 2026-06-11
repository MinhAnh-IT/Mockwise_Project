package com.mockwise.practice.service;

import com.mockwise.practice.common.config.PracticeProperties;
import com.mockwise.practice.entity.PracticeSubmission;
import com.mockwise.practice.enums.SubmissionStatus;
import com.mockwise.practice.repository.PracticeSubmissionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sweeps submissions stuck in JUDGING past the configured timeout — judge-service
 * crashed, the verdict was lost, or the broker dropped it. Such rows would
 * otherwise hang forever; flipping them to FAILED lets the UI stop polling.
 * A late verdict is still safe: {@code applyJudged} no-ops on a terminal row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubmissionWatchdog {

    PracticeSubmissionRepository submissionRepo;
    PracticeProperties properties;

    @Scheduled(fixedDelayString = "${practice.watchdog.interval-ms:30000}")
    @Transactional
    public void sweepStuckSubmissions() {
        LocalDateTime deadline = LocalDateTime.now()
                .minusSeconds(properties.watchdog().timeoutSeconds());
        List<PracticeSubmission> stuck =
                submissionRepo.findByStatusAndCreatedAtBefore(SubmissionStatus.JUDGING, deadline);
        if (stuck.isEmpty()) return;

        for (PracticeSubmission s : stuck) {
            s.setStatus(SubmissionStatus.FAILED);
            s.setFinishedAt(LocalDateTime.now());
        }
        submissionRepo.saveAll(stuck);
        log.warn("Watchdog failed {} submission(s) stuck in JUDGING past {}s",
                stuck.size(), properties.watchdog().timeoutSeconds());
    }
}
