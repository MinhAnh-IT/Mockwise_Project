package com.interview.judge.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.judge.admin.dto.Judge0Health;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Live capacity/health probe of the upstream Judge0 server. Reads
 * {@code /workers} (queue depth + worker counts) and {@code /about} (version).
 * Blocking with a short timeout — invoked off the request thread by the
 * controller is unnecessary here since judge-service is Spring MVC (one thread
 * per request), but the timeout keeps a wedged Judge0 from hanging the call.
 */
@Slf4j
@Component
public class Judge0HealthProbe {

    private final WebClient webClient;
    private final String baseUrl;
    private final String authToken;

    public Judge0HealthProbe(WebClient.Builder builder,
                             @Value("${judge0.base-url}") String baseUrl,
                             @Value("${judge0.auth-token:}") String authToken) {
        this.webClient = builder.build();
        this.baseUrl = baseUrl;
        this.authToken = authToken;
    }

    public Judge0Health probe() {
        long start = System.currentTimeMillis();
        try {
            JsonNode workers = get("/workers");
            long latency = System.currentTimeMillis() - start;

            Integer queueSize = null, total = null, idle = null, working = null;
            // /workers is an array of queue objects: [{ "queue", "size", "available", "idle", "working" }]
            if (workers != null && workers.isArray() && !workers.isEmpty()) {
                queueSize = 0; total = 0; idle = 0; working = 0;
                for (JsonNode q : workers) {
                    queueSize += q.path("size").asInt(0);
                    total += q.path("available").asInt(0);
                    idle += q.path("idle").asInt(0);
                    working += q.path("working").asInt(0);
                }
            }

            String version = null;
            try {
                JsonNode about = get("/about");
                if (about != null) {
                    version = about.path("version").asText(null);
                }
            } catch (Exception ignored) {
                // /about is best-effort; reachability is decided by /workers.
            }

            return new Judge0Health(true, latency, version, queueSize, total, idle, working, null);
        } catch (Exception e) {
            log.warn("Judge0 health probe failed: {}", e.getMessage());
            return new Judge0Health(false, null, null, null, null, null, null, e.getMessage());
        }
    }

    private JsonNode get(String path) {
        return webClient.get()
                .uri(baseUrl + path)
                .headers(h -> {
                    if (authToken != null && !authToken.isBlank()) {
                        h.set("X-Auth-Token", authToken);
                    }
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(3))
                .block();
    }
}
