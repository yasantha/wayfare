package com.wayfare.auth.spike;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * THROWAWAY Phase 0 spike security — permits everything so the spike endpoint is
 * reachable without a login. Delete with the rest of this package; Phase 1 adds
 * the real Auth Service security configuration.
 */
@Configuration
public class SpikeSecurityConfig {

    @Bean
    public SecurityFilterChain spikeFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
