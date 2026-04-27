package com.mockwise.gateway.filter;

import com.mockwise.gateway.filter.model.IntrospectData;
import com.mockwise.gateway.filter.model.IntrospectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGatewayFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    @Value("${app.iam.url}")
    private String iamUrl;

    /**
     * Temporary flag: when {@code true}, requests to {@code /api/v1/tts-stt/internal/**}
     * skip JWT/internal-path enforcement and the gateway injects {@code X-Internal-Auth}
     * from {@link #internalApiKey}. Set via {@code EXPOSE_TTS_STT_INTERNAL=true} in the
     * gateway env. Default: {@code false} — flip back when done testing.
     */
    @Value("${app.tts-stt.expose-internal:false}")
    private boolean exposeTtsSttInternal;

    @Value("${app.internal.api-key:}")
    private String internalApiKey;

    private static final String TTS_STT_INTERNAL_PREFIX = "/api/v1/tts-stt/internal/";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private record PublicRoute(HttpMethod method, String pattern) {}

    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/users/register"),
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/auth/sign-in"),
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/auth/logout"),
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/auth/token/renew"),
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/auth/token/introspect"),
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/auth/verify-account/send"),
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/auth/verify-account/confirm"),
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/auth/forgot-password/send"),
            new PublicRoute(HttpMethod.POST, "/api/v1/iam/auth/forgot-password/confirm"),
            new PublicRoute(HttpMethod.GET,  "/api/v1/position-tracks/**"),
            new PublicRoute(HttpMethod.GET,  "/api/v1/position-levels/**"),
            new PublicRoute(HttpMethod.GET,  "/api/v1/iam/auth/health"),
            new PublicRoute(HttpMethod.POST, "/api/v1/judge/callback/**"),
            new PublicRoute(HttpMethod.PUT,  "/api/v1/judge/callback/**"),

            // AI Evaluation — protected by X-API-Key at the AI service itself
            // (service-to-service; JWT introspection is skipped here)
            new PublicRoute(HttpMethod.GET,  "/api/ai/**"),
            new PublicRoute(HttpMethod.POST, "/api/ai/**"),

            // Question Bank — service-to-service read endpoints (used by Interview/AI Service)
            // Lists and test cases are admin-only; users access questions only through Interview Service
            new PublicRoute(HttpMethod.GET,  "/api/v1/question-bank/questions/health"),
            new PublicRoute(HttpMethod.GET,  "/api/v1/question-bank/questions/*"),
            new PublicRoute(HttpMethod.GET,  "/api/v1/question-bank/questions/*/for-ai"),
            new PublicRoute(HttpMethod.GET,  "/api/v1/question-bank/questions/*/snapshot"),
            new PublicRoute(HttpMethod.GET,  "/api/v1/question-bank/questions/*/audio-key"),

            // Storage Service — liveness probe (uploads/** still require JWT)
            new PublicRoute(HttpMethod.GET,  "/api/v1/storage/health"),

            // TTS-STT Service — liveness probe (internal/** is blocked by isInternalPath)
            new PublicRoute(HttpMethod.GET,  "/api/v1/tts-stt/health")
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Strip user-context headers from incoming request to prevent header injection
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Role");
                    h.remove("X-User-Email");
                    h.remove("X-Internal-Auth");
                })
                .build();

        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized).build();

        String pathValue = sanitized.getPath().value();

        // Temporary public exposure of tts-stt internal endpoints for end-to-end
        // testing on the deployed VPS. Forwards with X-Internal-Auth re-injected from
        // gateway env so the downstream filter accepts the request. Disable by setting
        // EXPOSE_TTS_STT_INTERNAL=false (or removing it) on api-gateway.
        if (exposeTtsSttInternal && pathValue.startsWith(TTS_STT_INTERNAL_PREFIX)) {
            if (internalApiKey == null || internalApiKey.isBlank()) {
                log.error("EXPOSE_TTS_STT_INTERNAL=true but INTERNAL_API_KEY not configured");
                return writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "Gateway misconfigured");
            }
            log.warn("Exposing internal tts-stt endpoint publicly: {} (testing mode)", pathValue);
            ServerHttpRequest withInternalAuth = sanitized.mutate()
                    .header("X-Internal-Auth", internalApiKey)
                    .build();
            return chain.filter(sanitizedExchange.mutate().request(withInternalAuth).build());
        }

        // Internal service-to-service paths must never be reachable from the public network.
        if (isInternalPath(pathValue)) {
            return writeError(exchange, HttpStatus.NOT_FOUND, "Not found");
        }

        if (isPublicRoute(sanitized.getMethod(), pathValue)) {
            return chain.filter(sanitizedExchange);
        }

        String authHeader = sanitized.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String path = pathValue;

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

                    // Admin-only path check — any path containing /admin/ requires ROLE_ADMIN
                    if (isAdminPath(path) && !"ADMIN".equalsIgnoreCase(data.getRole())) {
                        return writeError(exchange, HttpStatus.FORBIDDEN, "Access denied: admin only");
                    }

                    log.debug("Authenticated: userId={}, role={}, path={}", data.getUserId(), data.getRole(), path);

                    ServerHttpRequest mutated = sanitized.mutate()
                            .header("X-User-Id",    data.getUserId())
                            .header("X-User-Role",  data.getRole())
                            .header("X-User-Email", data.getUsername())
                            .build();

                    return chain.filter(sanitizedExchange.mutate().request(mutated).build());
                })
                .onErrorResume(e -> {
                    log.error("Auth introspect error: {}", e.getMessage());
                    return writeError(exchange, HttpStatus.BAD_GATEWAY, "Auth service unavailable");
                });
    }

    private boolean isPublicRoute(HttpMethod method, String path) {
        return PUBLIC_ROUTES.stream()
                .anyMatch(r -> r.method().equals(method) && PATH_MATCHER.match(r.pattern(), path));
    }

    // Rule: any path segment named "admin" requires ROLE_ADMIN
    // e.g. /api/v1/admin/profiles, /api/v1/admin/position-tracks, etc.
    private boolean isAdminPath(String path) {
        return PATH_MATCHER.match("/**/admin/**", path) || path.contains("/admin/");
    }

    // Rule: any path segment named "internal" is service-to-service only —
    // never reachable from the public network.
    private boolean isInternalPath(String path) {
        return path.contains("/internal/");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"code\":%d,\"message\":\"%s\"}", status.value(), message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // Run before other filters
    }
}
