package com.mockwise.practice.common.security;

import com.mockwise.practice.common.config.InternalAuthProperties;
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
                        // Matchers run AFTER context-path is stripped, so a request
                        // for /api/v1/practice/problems arrives here as /problems.
                        .requestMatchers(HttpMethod.GET, "/health", "/actuator/**").permitAll()

                        // Service-to-service endpoints — guarded by InternalAuthFilter.
                        .requestMatchers("/internal/**").hasAuthority("ROLE_INTERNAL")

                        // Everything else under /api/v1/practice/* is user-facing.
                        .anyRequest().hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                )

                .addFilterBefore(new InternalAuthFilter(internalAuthProperties), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new UserContextFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
