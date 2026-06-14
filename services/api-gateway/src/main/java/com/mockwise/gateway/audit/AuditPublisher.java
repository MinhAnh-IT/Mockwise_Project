package com.mockwise.gateway.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Set;

/**
 * Emits a coarse access-audit event for every <em>mutating</em> {@code /admin/**}
 * request that passes the gateway. Captures who (actor headers the gateway just
 * injected), what (method + path), the outcome (response status), and context
 * (client IP, user-agent, latency). The semantic classification is done later by
 * IAM's consumer.
 *
 * <p>Publishing is fire-and-forget and fully guarded — a Kafka outage must never
 * affect the proxied request. Called from {@code AuthGatewayFilter} only after
 * the response has completed, so the status code is final.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditPublisher {

    private static final String TOPIC = "audit-log";

    private static final Set<HttpMethod> MUTATING =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    private final KafkaTemplate<String, Object> auditKafkaTemplate;

    /** True when this request should produce an audit row: a mutating admin call. */
    public boolean shouldAudit(HttpMethod method, boolean adminPath) {
        return adminPath && method != null && MUTATING.contains(method);
    }

    public void publish(ServerHttpRequest request, String actorId, String actorEmail, String actorRole,
                        Integer statusCode, long latencyMs) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .source("gateway")
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .actorRole(actorRole)
                    .httpMethod(request.getMethod().name())
                    .path(request.getPath().value())
                    .statusCode(statusCode)
                    .ip(clientIp(request))
                    .userAgent(request.getHeaders().getFirst("User-Agent"))
                    .latencyMs(latencyMs)
                    .occurredAt(Instant.now().toString())
                    .build();

            auditKafkaTemplate.send(TOPIC, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish audit event: path={}, error={}",
                                    event.getPath(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to enqueue audit event: {}", e.getMessage());
        }
    }

    private static String clientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // First hop is the original client.
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : null;
    }
}
