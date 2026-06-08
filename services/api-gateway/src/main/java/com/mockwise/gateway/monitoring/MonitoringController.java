package com.mockwise.gateway.monitoring;

import com.core.apiresponse.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Admin server-monitoring snapshot, served locally by the gateway (not proxied).
 * Reachable at {@code /api/admin/monitoring/overview}: it doesn't match any
 * gateway route, so the request falls through to this controller. Admin access
 * is enforced by {@link MonitoringAuthWebFilter} (the routing GlobalFilter does
 * not run for locally-handled paths).
 */
@RestController
@RequestMapping("/api/admin/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/overview")
    public Mono<ApiResponse<MonitoringOverview>> overview() {
        // The probes block (HTTP/TCP/JMX) — run them off the event loop.
        return Mono.fromCallable(monitoringService::overview)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::success);
    }
}
