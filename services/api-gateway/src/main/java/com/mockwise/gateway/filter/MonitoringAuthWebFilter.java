package com.mockwise.gateway.filter;

import com.mockwise.gateway.filter.model.IntrospectData;
import com.mockwise.gateway.filter.model.IntrospectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Admin gate for gateway-local endpoints under {@code /api/admin/**} (e.g. the
 * monitoring dashboard). These paths match no route, so the routing
 * {@link AuthGatewayFilter} (a GlobalFilter) never runs for them — a WebFilter
 * does run for every exchange, so we re-introspect the bearer token here and
 * require ROLE_ADMIN. All other paths pass straight through to normal routing.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class MonitoringAuthWebFilter implements WebFilter {

    private static final String LOCAL_ADMIN_PREFIX = "/api/admin/";

    private final WebClient webClient;

    @Value("${app.iam.url}")
    private String iamUrl;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(LOCAL_ADMIN_PREFIX)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        return webClient.post()
                .uri(iamUrl + "/api/v1/iam/auth/token/introspect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("bearerToken", authHeader))
                .retrieve()
                .bodyToMono(IntrospectResponse.class)
                .flatMap(resp -> {
                    IntrospectData data = resp.getData();
                    if (data == null || !data.isActive()) {
                        return writeError(exchange, HttpStatus.UNAUTHORIZED, "Token is inactive or expired");
                    }
                    if (!"ADMIN".equalsIgnoreCase(data.getRole())) {
                        return writeError(exchange, HttpStatus.FORBIDDEN, "Access denied: admin only");
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    log.error("Monitoring auth introspect error: {}", e.getMessage());
                    return writeError(exchange, HttpStatus.BAD_GATEWAY, "Auth service unavailable");
                });
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"code\":%d,\"message\":\"%s\"}", status.value(), message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
