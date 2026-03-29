package com.mockwise.userprofile.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Public GET endpoints for Track and Level selection (no auth required)
                        .requestMatchers(HttpMethod.GET, "/position-tracks/**", "/position-tracks").permitAll()
                        .requestMatchers(HttpMethod.GET, "/position-levels/**", "/position-levels").permitAll()
                        
                        // User Profile APIs
                        .requestMatchers(HttpMethod.POST, "/profiles/**").hasAuthority("ROLE_SERVICE")
                        .requestMatchers(HttpMethod.GET, "/profiles/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN", "ROLE_SERVICE")
                        .requestMatchers(HttpMethod.PATCH, "/profiles/**").hasAuthority("ROLE_USER")
                        .requestMatchers(HttpMethod.PUT, "/profiles/**").hasAuthority("ROLE_USER")
                        
                        // Admin-only endpoints
                        .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
                        
                        .anyRequest().authenticated()
                )

                // Inject user info from Gateway headers
                .addFilterBefore(new UserContextFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
