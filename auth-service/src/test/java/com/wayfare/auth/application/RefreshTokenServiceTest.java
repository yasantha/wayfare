package com.wayfare.auth.application;

import com.wayfare.auth.config.JwtProperties;
import com.wayfare.auth.domain.RefreshToken;
import com.wayfare.auth.repository.RefreshTokenRepository;
import com.wayfare.commons.error.Exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository repository;

    JwtProperties props = new JwtProperties(null, null, "iss", "aud",
            Duration.ofMinutes(15), Duration.ofDays(30));

    private RefreshTokenService service() {
        return new RefreshTokenService(repository, props);
    }

    @Test
    void rotate_activeToken_revokesCurrentAndIssuesSuccessorInSameFamily() {
        UUID userId = UUID.randomUUID();
        RefreshToken active = RefreshToken.newFamily(userId, "hash", Instant.now().plusSeconds(3600), "dev");
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(active));

        RefreshTokenService.Rotation rotation = service().rotate("raw-token", "dev");

        assertThat(rotation.userId()).isEqualTo(userId);
        assertThat(rotation.rawRefreshToken()).isNotBlank();
        assertThat(active.getRevokedAt()).isNotNull();          // presented token burned
        verify(repository, times(2)).save(any(RefreshToken.class)); // revoke current + save successor
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void rotate_reusedRevokedToken_revokesWholeFamilyAndThrows() {
        RefreshToken revoked = RefreshToken.newFamily(UUID.randomUUID(), "hash",
                Instant.now().plusSeconds(3600), "dev");
        revoked.setRevokedAt(Instant.now().minusSeconds(10));    // already rotated away
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service().rotate("raw-token", "dev"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("reuse");

        verify(repository).revokeFamily(eq(revoked.getFamilyId()), any());
    }

    @Test
    void rotate_unknownToken_throwsUnauthorized() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().rotate("raw-token", "dev"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
