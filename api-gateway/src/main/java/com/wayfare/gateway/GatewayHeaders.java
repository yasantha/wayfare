package com.wayfare.gateway;

/** Identity headers the gateway injects downstream after validating the JWT. */
public final class GatewayHeaders {

    private GatewayHeaders() {
    }

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLES = "X-User-Roles";
}
