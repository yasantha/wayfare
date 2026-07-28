package com.wayfare.user;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Consumer half of the Phase 3 messaging backbone (design M4 milestone): a
 * real message on {@code user.registered} is consumed, the profile is created,
 * and — critically — redelivering the identical event (same eventId, as a
 * broker or consumer restart would do) is a no-op because of the
 * processed_events idempotency ledger, not because the raw profile-creation
 * happens to be naturally idempotent.
 */
@SpringBootTest
@Testcontainers
class UserRegisteredKafkaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // real broker here — do NOT disable listener auto-startup like UserServiceIT does
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    KafkaProducer<String, String> producer;

    @AfterEach
    void closeProducer() {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    void redeliveredEvent_createsProfileOnceAndIsIdempotent() throws Exception {
        producer = rawProducer();
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String message = """
                {"eventId":"%s","eventType":"user.registered","version":1,
                 "occurredAt":"2026-01-01T00:00:00Z","correlationId":"corr-1",
                 "userId":"%s","email":"kafka-it@example.com"}
                """.formatted(eventId, userId);

        // .get() confirms the broker actually acknowledged the send (including
        // implicit topic auto-creation) before we start waiting on the consumer
        // side — otherwise a slow first-send can silently race the assertion.
        producer.send(new ProducerRecord<>("user.registered", userId.toString(), message))
                .get(10, java.util.concurrent.TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Integer profiles = jdbcTemplate.queryForObject(
                    "select count(*) from user_profiles where user_id = ?", Integer.class, userId);
            assertThat(profiles).isEqualTo(1);
        });
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer processed = jdbcTemplate.queryForObject(
                    "select count(*) from processed_events where event_id = ?", Integer.class, eventId);
            assertThat(processed).isEqualTo(1);
        });

        // Redeliver the identical event (same eventId) — must not error or duplicate.
        producer.send(new ProducerRecord<>("user.registered", userId.toString(), message))
                .get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Give the (skipped) redelivery a moment to be processed, then assert
        // counts are unchanged rather than merely "eventually 1" again.
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(13)).untilAsserted(() -> {
            Integer profiles = jdbcTemplate.queryForObject(
                    "select count(*) from user_profiles where user_id = ?", Integer.class, userId);
            Integer processed = jdbcTemplate.queryForObject(
                    "select count(*) from processed_events where event_id = ?", Integer.class, eventId);
            assertThat(profiles).isEqualTo(1);
            assertThat(processed).isEqualTo(1);
        });
    }

    private KafkaProducer<String, String> rawProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }
}
