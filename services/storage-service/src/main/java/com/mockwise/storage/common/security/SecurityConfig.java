package com.mockwise.storage.common.security;

import com.mockwise.storage.common.config.InternalAuthProperties;
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
                        .requestMatchers(HttpMethod.GET, "/health", "/actuator/**").permitAll()

                        // Internal service-to-service endpoints — guarded by InternalAuthFilter
                        .requestMatchers("/internal/**").hasAuthority("ROLE_INTERNAL")

                        // User-facing video / avatar upload — JWT propagated by api-gateway
                        .requestMatchers("/uploads/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        // Avatar download (server-side proxy back to MinIO)
                        .requestMatchers("/avatars/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        // Question-audio download — same access semantics the
                        // old presigned MinIO URLs had: anyone holding the
                        // random object key can play the clip. Object keys
                        // are UUIDs so the surface is not enumerable, and
                        // serving without JWT lets <audio src="..."> load
                        // it directly without a blob-URL dance.
                        .requestMatchers(HttpMethod.GET, "/question-audio/**").permitAll()

                        .anyRequest().authenticated()
                )

                .addFilterBefore(new InternalAuthFilter(internalAuthProperties), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new UserContextFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
