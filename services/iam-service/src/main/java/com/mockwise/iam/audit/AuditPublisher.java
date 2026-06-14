package com.mockwise.iam.audit;

import com.mockwise.iam.message.constants.KafkaTopics;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publishes IAM's own auth audit events ({@code LOGIN} / {@code LOGIN_FAILED} /
 * {@code LOGOUT}) to the shared {@code audit-log} topic, where {@link AuditConsumer}
 * persists them. These are not {@code /admin/} routes, so the gateway never sees
 * them — IAM is the authoritative source. Fire-and-forget: a publish failure is
 * logged but never breaks the auth flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    /** Record an auth-category event. {@code outcome} is {@code SUCCESS} / {@code FAILURE}. */
    public void publishAuth(String action, String outcome, String actorId, String actorEmail,
                            String actorRole, String detail) {
        AuditEvent event = AuditEvent.builder()
                .source("iam-auth")
                .action(action)
                .category("AUTH")
                .outcome(outcome)
                .actorId(actorId)
                .actorEmail(actorEmail)
                .actorRole(actorRole)
                .detail(detail)
                .occurredAt(Instant.now().toString())
                .build();
        publish(event, action, actorEmail);
    }

    private void publish(AuditEvent event, String action, String actorEmail) {
        try {
            kafkaTemplate.send(KafkaTopics.AUDIT, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish audit event: action={}, actor={}, error={}",
                                    action, actorEmail, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to enqueue audit event: action={}, actor={}, error={}",
                    action, actorEmail, e.getMessage());
        }
    }
}
