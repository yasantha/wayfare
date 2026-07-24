package com.wayfare.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Start a brand-new rotation family (issued at login). */
    public static RefreshToken newFamily(UUID userId, String tokenHash, Instant expiresAt, String deviceInfo) {
        return build(userId, tokenHash, UUID.randomUUID(), expiresAt, deviceInfo);
    }

    /** Continue an existing family (issued on rotation). */
    public static RefreshToken inFamily(UUID userId, String tokenHash, UUID familyId, Instant expiresAt, String deviceInfo) {
        return build(userId, tokenHash, familyId, expiresAt, deviceInfo);
    }

    private static RefreshToken build(UUID userId, String tokenHash, UUID familyId, Instant expiresAt, String deviceInfo) {
        RefreshToken t = new RefreshToken();
        t.id = UUID.randomUUID();
        t.userId = userId;
        t.tokenHash = tokenHash;
        t.familyId = familyId;
        t.expiresAt = expiresAt;
        t.deviceInfo = deviceInfo;
        return t;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
