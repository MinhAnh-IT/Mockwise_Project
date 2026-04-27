package com.mockwise.ttsstt.stt.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic sweepers backing the STT pipeline:
 *   - re-publish events for jobs whose Kafka publish was lost (process crash
 *     between DB commit and Kafka send).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SttBackgroundJobs {

    SttOrchestrator orchestrator;

    /** Run once a minute — cheap query, only acts on rows missing publishedEventAt. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void republishMissedEvents() {
        try {
            orchestrator.republishMissedEvents();
        } catch (Exception ex) {
            log.warn("republishMissedEvents sweep failed", ex);
        }
    }
}
