package com.wayfare.auth.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wayfare.auth.config.JwtProperties;
import com.wayfare.auth.domain.User;
import com.wayfare.auth.infrastructure.security.RsaKeyProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Issues short-lived RS256 access tokens (design §6.1). */
@Service
public class JwtService {

    private final RsaKeyProvider keys;
    private final JwtProperties props;

    public JwtService(RsaKeyProvider keys, JwtProperties props) {
        this.keys = keys;
        this.props = props;
    }

    public IssuedToken issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTokenTtl());
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getId().toString())
                    .claim("email", user.getEmail())
                    .claim("roles", List.of(user.getRole().name()))
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .issuer(props.issuer())
                    .audience(props.audience())
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keys.keyId())
                    .type(com.nimbusds.jose.JOSEObjectType.JWT)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(keys.privateKey()));
            return new IssuedToken(jwt.serialize(), exp);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
    }

    public long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
