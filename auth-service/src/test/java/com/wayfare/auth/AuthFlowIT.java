package com.wayfare.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end auth flow against a real PostgreSQL (Testcontainers): register,
 * login, rotate the refresh token, and confirm reuse of a rotated token is
 * rejected. A throwaway RSA keypair is generated at runtime so no secret is
 * committed. Kafka is not required — registration writes to the outbox table,
 * not the broker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static Path keyDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        keyDir = generateKeypair();
        registry.add("jwt.private-key-path", () -> keyDir.resolve("private.pem").toString());
        registry.add("jwt.public-key-path", () -> keyDir.resolve("public.pem").toString());
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void register_login_rotate_and_reuseIsRejected() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        Map<String, String> creds = Map.of("email", email, "password", "password123");

        // Register -> 201 with tokens
        ResponseEntity<Map> reg = rest.postForEntity("/auth/register", creds, Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reg.getBody()).containsKeys("accessToken", "refreshToken", "userId");
        String firstRefresh = (String) reg.getBody().get("refreshToken");

        // Login -> 200 with a fresh access token
        ResponseEntity<Map> login = rest.postForEntity("/auth/login", creds, Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("accessToken")).isNotNull();

        // Rotate the refresh token -> new token, different from the first
        ResponseEntity<Map> refreshed = rest.postForEntity("/auth/refresh",
                Map.of("refreshToken", firstRefresh), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secondRefresh = (String) refreshed.getBody().get("refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Reuse of the now-rotated token is detected and rejected (401)
        ResponseEntity<Map> reuse = rest.postForEntity("/auth/refresh",
                Map.of("refreshToken", firstRefresh), Map.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void jwks_endpoint_servesPublicKey() {
        ResponseEntity<Map> jwks = rest.getForEntity("/.well-known/jwks.json", Map.class);
        assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jwks.getBody()).containsKey("keys");
    }

    private static Path generateKeypair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        Path dir = Files.createTempDirectory("wayfare-test-keys");
        Base64.Encoder enc = Base64.getMimeEncoder(64, "\n".getBytes());
        Files.writeString(dir.resolve("private.pem"),
                "-----BEGIN PRIVATE KEY-----\n"
                        + enc.encodeToString(pair.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----\n");
        Files.writeString(dir.resolve("public.pem"),
                "-----BEGIN PUBLIC KEY-----\n"
                        + enc.encodeToString(pair.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n");
        return dir;
    }
}
