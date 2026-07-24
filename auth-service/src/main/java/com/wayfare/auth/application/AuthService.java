package com.wayfare.auth.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.auth.domain.User;
import com.wayfare.auth.domain.UserStatus;
import com.wayfare.auth.domain.OutboxEvent;
import com.wayfare.auth.repository.OutboxRepository;
import com.wayfare.auth.repository.UserRepository;
import com.wayfare.commons.correlation.Correlation;
import com.wayfare.commons.error.Exceptions.ConflictException;
import com.wayfare.commons.error.Exceptions.UnauthorizedException;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Registration, login, token refresh, and logout (design §5.2 Auth). */
@Service
public class AuthService {

    private final UserRepository users;
    private final OutboxRepository outbox;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final ObjectMapper objectMapper;

    public AuthService(UserRepository users, OutboxRepository outbox, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokens, ObjectMapper objectMapper) {
        this.users = users;
        this.outbox = outbox;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.objectMapper = objectMapper;
    }

    /**
     * Registration writes the user AND the user.registered outbox event in one
     * transaction (design §3.3), so the event can never be lost after commit.
     */
    @Transactional
    public AuthResult register(String email, String rawPassword, String deviceInfo) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw new ConflictException("Email already registered");
        }
        User user = User.create(normalized, passwordEncoder.encode(rawPassword));
        users.save(user);

        outbox.save(OutboxEvent.of("user", user.getId().toString(), "user.registered",
                registeredPayload(user), MDC.get(Correlation.MDC_KEY)));

        return issueTokens(user, deviceInfo);
    }

    @Transactional
    public AuthResult login(String email, String rawPassword, String deviceInfo) {
        User user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Account is not active");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return issueTokens(user, deviceInfo);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken, String deviceInfo) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, deviceInfo);
        User user = users.findById(rotation.userId())
                .orElseThrow(() -> new UnauthorizedException("Unknown user"));
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        return new AuthResult(access.token(), rotation.rawRefreshToken(),
                jwtService.accessTokenTtlSeconds(), user.getId());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    private AuthResult issueTokens(User user, String deviceInfo) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        String refresh = refreshTokens.issueForLogin(user.getId(), deviceInfo);
        return new AuthResult(access.token(), refresh, jwtService.accessTokenTtlSeconds(), user.getId());
    }

    private String registeredPayload(User user) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "eventType", "user.registered",
                    "version", 1,
                    "occurredAt", Instant.now().toString(),
                    "userId", user.getId().toString(),
                    "email", user.getEmail()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize user.registered payload", e);
        }
    }

    public record AuthResult(String accessToken, String refreshToken, long expiresInSeconds, UUID userId) {
    }
}
