package com.mockwise.interview.common.security;

import com.mockwise.interview.common.config.InternalAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalAuthProperties internalAuthProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Path matchers run AFTER context-path is stripped, so a
                        // request for /api/v1/interviews/start arrives here as /start.
                        .requestMatchers(HttpMethod.GET, "/health", "/actuator/**").permitAll()

                        // Internal service-to-service endpoints — guarded by InternalAuthFilter
                        .requestMatchers("/internal/**").hasAuthority("ROLE_INTERNAL")

                        // Everything else under /api/v1/interviews/* is user-facing.
                        // The interview-service is single-purpose so this catch-all
                        // is safe — no admin / public sub-tree to disambiguate.
                        .anyRequest().hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                )

                .addFilterBefore(new InternalAuthFilter(internalAuthProperties), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new UserContextFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
