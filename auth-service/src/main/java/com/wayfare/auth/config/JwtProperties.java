package com.wayfare.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Binds the {@code jwt.*} block from application.yml. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKeyPath,
        String publicKeyPath,
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
