package com.mockwise.interview.message.publisher;

import com.mockwise.interview.entity.OutboxEvent;
import com.mockwise.interview.repository.OutboxEventRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Drains the {@code outbox_event} table and publishes each row to Kafka,
 * marking it published once the broker has acked. Per the design's
 * §6 outbox pattern, every business write that produces an event also
 * inserts a row here in the same transaction — this poller is the only
 * thing that ever talks to Kafka on the producer side.
 *
 * <p>The partial index on {@code (published, created_at) WHERE published
 * = false} keeps the unread queue scan O(unread) regardless of how big
 * the table grows. We sort by {@code created_at ASC} so events publish
 * in the order they were committed.
 *
 * <p>Sync send: we wait for the broker ack before flipping
 * {@code published = true}. The slower path (vs. fire-and-forget) is
 * the price of guaranteeing at-least-once delivery — a lost ack means
 * the row stays unpublished and the next tick retries.
 *
 * <p>Failure handling is deliberately minimal: we log the exception and
 * leave the row unpublished so the next tick retries. After 100
 * unpublished rows accumulate, an alert (operator-side, e.g. metric
 * scrape) should fire — the design's §12 "outbox lag > 30s" gauge.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxPoller {

    OutboxEventRepository outboxRepo;
    KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Cap per tick — keeps a single slow tick from monopolising the executor.
     * {@code @NonFinal} opts out of the class-wide {@code makeFinal = true}
     * so {@code @RequiredArgsConstructor} doesn't pull this into the
     * constructor signature; Spring populates it via {@code @Value} injection.
     */
    @NonFinal
    @Value("${interview.outbox.batch-size:50}")
    int batchSize;

    /** How long to wait for a broker ack before giving up on the row. */
    @NonFinal
    @Value("${interview.outbox.send-timeout-ms:5000}")
    long sendTimeoutMs;

    /**
     * Each tick is its own transaction so a slow row doesn't hold a long
     * lived JPA Tx open. The {@code save} on the marked row commits at
     * tick end.
     */
    @Scheduled(fixedDelayString = "${interview.outbox.poll-interval-ms:1000}")
    @Transactional
    public void poll() {
        List<OutboxEvent> batch = outboxRepo.findUnpublished(PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox poller: draining {} events", batch.size());

        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.setPublished(true);
                event.setPublishedAt(OffsetDateTime.now());
                outboxRepo.save(event);
            } catch (Exception ex) {
                // Leave the row unpublished — the next tick retries. We
                // log at WARN (not ERROR) because this is the expected
                // recovery path; ERROR is reserved for things an operator
                // needs to investigate.
                log.warn("Outbox publish failed for event id={} topic={} type={}: {}",
                        event.getId(), event.getTopic(), event.getEventType(), ex.getMessage());
            }
        }
    }

    /**
     * Key the Kafka record by {@code aggregate_id} so all events for one
     * answer / session land on the same partition — gives the consumer
     * per-aggregate ordering for free.
     */
    private void send(OutboxEvent event) throws ExecutionException, InterruptedException, TimeoutException {
        String key = event.getAggregateId() != null ? event.getAggregateId().toString() : null;
        kafkaTemplate.send(event.getTopic(), key, event.getPayload())
                .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
    }
}
