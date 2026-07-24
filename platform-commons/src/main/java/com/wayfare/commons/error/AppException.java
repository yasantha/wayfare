package com.wayfare.commons.error;

import org.springframework.http.HttpStatus;

/**
 * Base for all application exceptions. Each subclass carries the HTTP status
 * and a stable {@code type} slug used to build the RFC 9457 ProblemDetail.
 * Mapped to a uniform response by {@link GlobalExceptionHandler}.
 */
public abstract class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String type;

    protected AppException(HttpStatus status, String type, String message) {
        super(message);
        this.status = status;
        this.type = type;
    }

    protected AppException(HttpStatus status, String type, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.type = type;
    }

    public HttpStatus status() {
        return status;
    }

    /** Stable error-type slug, e.g. {@code resource-not-found}. */
    public String type() {
        return type;
    }
}
