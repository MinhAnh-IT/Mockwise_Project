package com.mockwise.interview.service;

import com.mockwise.interview.entity.OutboxEvent;
import com.mockwise.interview.repository.OutboxEventRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * One-line helper for staging an outbox event in the same transaction as
 * the business write that triggered it. Kept thin on purpose — the
 * background poller (Phase G) is the side that handles publish + retries.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxWriter {

    OutboxEventRepository outboxRepo;

    public OutboxEvent stage(String topic, String eventType, UUID aggregateId, Map<String, Object> payload) {
        OutboxEvent event = OutboxEvent.builder()
                .topic(topic)
                .eventType(eventType)
                .aggregateId(aggregateId)
                .payload(payload)
                .published(false)
                .build();
        return outboxRepo.save(event);
    }
}
