package com.wayfare.auth.api;

import com.wayfare.auth.infrastructure.security.RsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public JWKS endpoint. The gateway and every other service fetch the RS256
 * public key here to verify tokens — no signing secret is ever distributed.
 */
@RestController
public class JwksController {

    private final RsaKeyProvider keys;

    public JwksController(RsaKeyProvider keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return keys.publicJwkSet().toJSONObject();
    }
}
