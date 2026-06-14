package com.interview.judge.service;

import com.interview.judge.repository.JudgeJobRepository;
import com.interview.judge.repository.JudgeTaskResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sweeps away throwaway "Kiểm tra đề" (validation) jobs once the client has had
 * time to poll their result. A validation Run uses the same persisted judge
 * pipeline as a real submission, but its rows are noise — they belong to no
 * interview/practice answer and are never scored. We keep them just long enough
 * to be polled, then delete the job + its task rows (the FK has no ON DELETE
 * CASCADE, so task rows go first).
 *
 * <p>Retention is generous relative to a run (which finishes in seconds) so a
 * slow poll never races the purge.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EphemeralJobPurger {

    private final JudgeJobRepository judgeJobRepository;
    private final JudgeTaskResultRepository judgeTaskResultRepository;

    /** Delete ephemeral jobs older than this many minutes. */
    @Value("${judge.ephemeral.retention-minutes:15}")
    private long retentionMinutes;

    /** How often the sweep runs (ms). Default every 5 minutes. */
    @Scheduled(fixedDelayString = "${judge.ephemeral.purge-interval-ms:300000}")
    @Transactional
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(retentionMinutes);
        List<UUID> ids = judgeJobRepository.findEphemeralIdsOlderThan(cutoff);
        if (ids.isEmpty()) {
            return;
        }
        int tasks = judgeTaskResultRepository.deleteByJobIdIn(ids);
        int jobs = judgeJobRepository.deleteByIdIn(ids);
        log.info("Purged {} ephemeral judge job(s) + {} task row(s) older than {}",
                jobs, tasks, cutoff);
    }
}
