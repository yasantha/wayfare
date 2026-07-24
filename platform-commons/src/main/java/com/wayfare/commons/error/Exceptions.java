package com.wayfare.commons.error;

import org.springframework.http.HttpStatus;

/**
 * Concrete application exceptions mapped to HTTP status codes (design §9.2):
 * <pre>
 * ResourceNotFoundException -> 404   ValidationException     -> 400
 * UnauthorizedException     -> 401   ForbiddenException      -> 403
 * ConflictException         -> 409   QuotaExceededException  -> 429
 * ExternalServiceException  -> 503
 * </pre>
 * Grouped here so the whole hierarchy is visible at a glance.
 */
public final class Exceptions {

    private Exceptions() {
    }

    public static class ResourceNotFoundException extends AppException {
        public ResourceNotFoundException(String message) {
            super(HttpStatus.NOT_FOUND, "resource-not-found", message);
        }
    }

    public static class ValidationException extends AppException {
        public ValidationException(String message) {
            super(HttpStatus.BAD_REQUEST, "validation-failed", message);
        }
    }

    public static class UnauthorizedException extends AppException {
        public UnauthorizedException(String message) {
            super(HttpStatus.UNAUTHORIZED, "unauthorized", message);
        }
    }

    public static class ForbiddenException extends AppException {
        public ForbiddenException(String message) {
            super(HttpStatus.FORBIDDEN, "forbidden", message);
        }
    }

    public static class ConflictException extends AppException {
        public ConflictException(String message) {
            super(HttpStatus.CONFLICT, "conflict", message);
        }
    }

    public static class QuotaExceededException extends AppException {
        public QuotaExceededException(String message) {
            super(HttpStatus.TOO_MANY_REQUESTS, "quota-exceeded", message);
        }
    }

    public static class ExternalServiceException extends AppException {
        public ExternalServiceException(String message) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "external-service-error", message);
        }

        public ExternalServiceException(String message, Throwable cause) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "external-service-error", message, cause);
        }
    }
}
