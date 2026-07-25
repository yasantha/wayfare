package com.wayfare.gateway.filter;

import com.wayfare.gateway.GatewayHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Translates the validated JWT into trusted downstream headers
 * ({@code X-User-Id}, {@code X-User-Roles}). Client-supplied copies of these
 * headers are always stripped first, so a caller cannot spoof an identity — the
 * only values a service sees are the ones the gateway derived from a valid token.
 * Services still independently validate the JWT too (design §6.2).
 */
@Component
public class UserContextGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> withIdentity(exchange, auth.getToken()))
                .defaultIfEmpty(stripIdentity(exchange))
                .flatMap(chain::filter);
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt jwt) {
        String roles = String.join(",", rolesOf(jwt));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(GatewayHeaders.USER_ID);
                    h.remove(GatewayHeaders.USER_ROLES);
                })
                .header(GatewayHeaders.USER_ID, jwt.getSubject())
                .header(GatewayHeaders.USER_ROLES, roles)
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange stripIdentity(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(GatewayHeaders.USER_ID);
                    h.remove(GatewayHeaders.USER_ROLES);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private List<String> rolesOf(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null ? roles : List.of();
    }

    @Override
    public int getOrder() {
        // After security has authenticated, before the routing/proxy filter.
        return 10_000;
    }
}
