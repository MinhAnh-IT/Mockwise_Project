package com.mockwise.interview.service;

import com.mockwise.interview.entity.ProcessedEvent;
import com.mockwise.interview.repository.ProcessedEventRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-backed inbound event dedup. Picked over a Redis cache (the design
 * doc's other option) because the {@code processed_event} insert lives
 * in the same transaction as the business write — a crash between the
 * two is impossible by construction, where a Redis-cached id could
 * out-live a rolled-back business write and silently drop a redelivery.
 *
 * <p>Usage from a consumer:
 * <pre>
 *   if (dedup.markProcessed(eventId, eventType)) {
 *       answerService.applyEvaluationCompleted(...);  // first time
 *   } else {
 *       log.debug("duplicate {}", eventId);           // already done
 *   }
 *   ack.acknowledge();
 * </pre>
 *
 * <p>Note: the consumer SHOULD wrap its handler in {@code @Transactional}
 * with {@code REQUIRES_NEW} or call this method first inside the same Tx
 * as the business write — this method's own {@code REQUIRES_NEW} is for
 * the duplicate-check path so a unique-violation on retry doesn't
 * abort the outer Tx.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventDedupService {

    ProcessedEventRepository processedEventRepo;

    /**
     * Read-only fast-skip for redelivered events that already completed.
     * Does NOT insert — call {@link #markProcessed} only AFTER the business
     * write has committed, so a failed/rolled-back handler stays retryable
     * (an up-front insert would mark a never-finished event as done forever).
     */
    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return eventId != null && !eventId.isBlank() && processedEventRepo.existsById(eventId);
    }

    /**
     * @return {@code true} if this is the first time we see {@code eventId}
     *         (caller should proceed to handle), {@code false} if it was
     *         already processed (caller should ack and return).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessed(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            // Producer-side bug. Treat as "always handle" — better than
            // dropping the event silently. The handler may still error
            // out on bad payload, which logs.
            return true;
        }
        if (processedEventRepo.existsById(eventId)) {
            return false;
        }
        try {
            processedEventRepo.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType != null ? eventType : "UNKNOWN")
                    .build());
            return true;
        } catch (DataIntegrityViolationException race) {
            // Another concurrent consumer beat us to the insert. The
            // duplicate guard wins — let them handle, we skip.
            return false;
        }
    }
}
