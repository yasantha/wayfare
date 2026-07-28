package com.wayfare.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the outbox pattern end-to-end against real infrastructure (design
 * §3.3, Phase 3 milestone M4): register a user -> AuthService writes the
 * user.registered outbox row in the same transaction -> platform-commons'
 * OutboxPoller (auto-active here since JdbcTemplate + KafkaTemplate beans both
 * exist) picks it up and publishes it -> a raw consumer confirms the message
 * lands on the real topic, carrying the eventId the processed_events
 * idempotency ledger depends on, and the outbox row is marked published.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OutboxToKafkaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    static Path keyDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("wayfare.outbox.poll-interval-ms", () -> "200");

        keyDir = generateKeypair();
        registry.add("jwt.private-key-path", () -> keyDir.resolve("private.pem").toString());
        registry.add("jwt.public-key-path", () -> keyDir.resolve("public.pem").toString());
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbcTemplate;

    KafkaConsumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void registration_isPublishedToKafkaAndMarkedInOutbox() throws Exception {
        consumer = rawConsumer("user.registered");

        String email = "outbox-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<Map> reg = rest.postForEntity("/auth/register",
                Map.of("email", email, "password", "password123"), Map.class);
        String userId = String.valueOf(reg.getBody().get("userId"));

        ConsumerRecord<String, String> record = pollUntilFound(consumer, "user.registered", userId);

        assertThat(record.key()).isEqualTo(userId);
        JsonNode event = new ObjectMapper().readTree(record.value());
        assertThat(event.path("eventType").asText()).isEqualTo("user.registered");
        assertThat(event.path("email").asText()).isEqualTo(email);
        assertThat(event.path("userId").asText()).isEqualTo(userId);
        assertThat(event.hasNonNull("eventId")).isTrue();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer unpublished = jdbcTemplate.queryForObject(
                    "select count(*) from outbox where aggregate_id = ? and published_at is null",
                    Integer.class, userId);
            assertThat(unpublished).isZero();
        });
    }

    private static ConsumerRecord<String, String> pollUntilFound(KafkaConsumer<String, String> consumer,
                                                                  String topic, String key) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records) {
                if (key.equals(r.key())) {
                    return r;
                }
            }
        }
        throw new AssertionError("No message with key " + key + " found on topic " + topic + " within timeout");
    }

    private KafkaConsumer<String, String> rawConsumer(String... topics) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> c = new KafkaConsumer<>(props);
        c.subscribe(List.of(topics));
        return c;
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
