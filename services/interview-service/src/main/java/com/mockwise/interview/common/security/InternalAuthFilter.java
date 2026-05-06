package com.mockwise.interview.common.security;

import com.mockwise.interview.common.config.InternalAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates service-to-service calls hitting /internal/** by checking
 * the X-Internal-Auth header against the shared API key.
 *
 * <p>On success, seeds the SecurityContext with ROLE_INTERNAL so the
 * downstream authorization rules can permit the call.
 */
@Slf4j
@RequiredArgsConstructor
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Auth";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final InternalAuthProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!request.getRequestURI().contains(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (provided != null && !provided.isBlank() && provided.equals(properties.apiKey())) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "internal-service",
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            log.warn("Internal call rejected — missing/invalid {} header on {}", HEADER, request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
