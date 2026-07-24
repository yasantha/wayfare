package com.wayfare.auth.api.dto;

import com.wayfare.auth.application.AuthService.AuthResult;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId
) {
    public static TokenResponse from(AuthResult r) {
        return new TokenResponse(r.accessToken(), r.refreshToken(), "Bearer",
                r.expiresInSeconds(), r.userId());
    }
}
