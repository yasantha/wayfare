package com.wayfare.commons.security;

import com.wayfare.commons.correlation.Correlation;
import com.wayfare.commons.error.Exceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

/**
 * Reads the authenticated caller from the headers the gateway injects after it
 * validates the JWT ({@code X-User-Id}, {@code X-User-Roles}).
 *
 * <p>Note: this is a convenience for the request-scoped user id used in ownership
 * checks. Each service still independently validates the JWT signature via Spring
 * Security (design §6.2) — trusting these headers alone is not sufficient auth.
 */
public final class UserContext {

    private UserContext() {
    }

    /** The authenticated user id, or throws 401 if absent/malformed. */
    public static UUID requireUserId(HttpServletRequest request) {
        String raw = request.getHeader(Correlation.USER_ID_HEADER);
        if (raw == null || raw.isBlank()) {
            throw new UnauthorizedException("Missing authenticated user id");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Malformed user id");
        }
    }

    /** Roles carried on the request, e.g. {@code [ROLE_USER]}; empty if none. */
    public static List<String> roles(HttpServletRequest request) {
        String raw = request.getHeader(Correlation.USER_ROLES_HEADER);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split("\\s*,\\s*"));
    }
}
