package com.wayfare.auth.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.wayfare.auth.config.JwtProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RS256 keypair (PKCS#8 private, X.509 public PEM) once at startup and
 * exposes it for signing and for the JWKS endpoint. Auth Service is the only
 * holder of the private key; everyone else verifies via the published public JWK.
 */
@Component
public class RsaKeyProvider {

    private final RSAPrivateKey privateKey;
    private final RSAKey rsaJwk;

    public RsaKeyProvider(JwtProperties props) throws Exception {
        this.privateKey = loadPrivateKey(props.privateKeyPath());
        RSAPublicKey publicKey = loadPublicKey(props.publicKeyPath());

        RSAKey base = new RSAKey.Builder(publicKey)
                .privateKey(this.privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        // Deterministic key id (thumbprint) so tokens name the key that signed them.
        String kid = base.computeThumbprint().toString();
        this.rsaJwk = new RSAKey.Builder(base).keyID(kid).build();
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public String keyId() {
        return rsaJwk.getKeyID();
    }

    /** Public JWK set served at /.well-known/jwks.json. Never exposes the private key. */
    public JWKSet publicJwkSet() {
        return new JWKSet(rsaJwk.toPublicJWK());
    }

    private static RSAPrivateKey loadPrivateKey(String path) throws Exception {
        byte[] der = pemToDer(Files.readString(Path.of(path)),
                "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey loadPublicKey(String path) throws Exception {
        byte[] der = pemToDer(Files.readString(Path.of(path)),
                "-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----");
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] pemToDer(String pem, String begin, String end) {
        String base64 = pem
                .replace(begin, "")
                .replace(end, "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
