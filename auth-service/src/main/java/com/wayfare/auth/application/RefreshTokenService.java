package com.wayfare.auth.application;

import com.wayfare.auth.config.JwtProperties;
import com.wayfare.auth.domain.RefreshToken;
import com.wayfare.auth.repository.RefreshTokenRepository;
import com.wayfare.commons.error.Exceptions.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens: 256 bits of randomness, stored only as a SHA-256 hash,
 * rotated on every use. Presenting an already-rotated (revoked) token is treated
 * as theft and revokes the entire rotation family (design §6.1).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final JwtProperties props;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** Issue a fresh token starting a new family (called at login). */
    @Transactional
    public String issueForLogin(UUID userId, String deviceInfo) {
        String raw = randomToken();
        RefreshToken token = RefreshToken.newFamily(userId, hash(raw), expiry(), deviceInfo);
        repository.save(token);
        return raw;
    }

    /** Validate + rotate. Returns the new raw token and its owner. */
    @Transactional
    public Rotation rotate(String rawToken, String deviceInfo) {
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        Instant now = Instant.now();

        // Reuse of a rotated/revoked token => likely theft: burn the whole family.
        if (current.getRevokedAt() != null) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected; revoked {} tokens in family {}",
                    revoked, current.getFamilyId());
            throw new UnauthorizedException("Refresh token reuse detected");
        }
        if (!current.isActive(now)) {
            throw new UnauthorizedException("Expired refresh token");
        }

        // Rotate: revoke the presented token, issue a successor in the same family.
        current.setRevokedAt(now);
        repository.save(current);

        String raw = randomToken();
        RefreshToken next = RefreshToken.inFamily(
                current.getUserId(), hash(raw), current.getFamilyId(), expiry(), deviceInfo);
        repository.save(next);

        return new Rotation(current.getUserId(), raw);
    }

    /** Logout: revoke a single presented token (no-op if unknown). */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
                repository.save(t);
            }
        });
    }

    private Instant expiry() {
        return Instant.now().plus(props.refreshTokenTtl());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Rotation(UUID userId, String rawRefreshToken) {
    }
}
