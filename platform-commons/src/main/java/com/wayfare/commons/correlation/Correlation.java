package com.wayfare.commons.correlation;

/** Shared constants for correlation-ID propagation across HTTP and Kafka. */
public final class Correlation {

    private Correlation() {
    }

    /** HTTP header and Kafka header key carrying the correlation id. */
    public static final String HEADER = "X-Correlation-Id";

    /** MDC key so the id appears on every structured log line. */
    public static final String MDC_KEY = "correlationId";

    /** Header the gateway injects after validating the JWT. */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** Header the gateway injects with the caller's roles (comma-separated). */
    public static final String USER_ROLES_HEADER = "X-User-Roles";
}
