package com.mail_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Unauthenticated liveness probe. mail-service is otherwise a pure Kafka
 * consumer with no HTTP API, so the admin monitoring dashboard had nothing to
 * hit but {@code /} (which 404s). This gives it a clean 200 to probe.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "mail-service"));
    }
}
