package com.mockwise.ttsstt.common.security;

import com.mockwise.ttsstt.common.config.InternalAuthProperties;
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
                        .requestMatchers("/internal/**").hasAuthority("ROLE_INTERNAL")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new InternalAuthFilter(internalAuthProperties), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
