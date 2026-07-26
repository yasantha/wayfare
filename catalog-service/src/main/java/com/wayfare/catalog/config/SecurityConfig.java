package com.wayfare.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Catalog reads are public data (no per-user ownership), so authentication is
 * only required to reach nothing extra here beyond the gateway's own edge
 * check — but the resource-server config still validates any token presented,
 * and {@code /internal/**} is reachable only inside the cluster (gateway-blocked).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/destinations/**", "/activities/**").permitAll()
                        .requestMatchers("/internal/**", "/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }));
        return http.build();
    }
}
