package com.wayfare.trip;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Consumer side of the generation saga (design §4.4 steps 3-4), against a real
 * broker. Itinerary AI doesn't exist until Phase 5, so this test hand-builds
 * the events AI will eventually produce — same approach as
 * UserRegisteredKafkaIT in Phase 3, proving the consumer is correct ahead of
 * its producer existing.
 */
@SpringBootTest
@Testcontainers
class ItineraryGenerationKafkaIT {

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
    void succeededEvent_persistsAndActivatesNewAiVersion_andMarksTripReady() throws Exception {
        producer = rawProducer();
        UUID tripId = insertDraftTrip();

        String message = """
                {"eventId":"%s","eventType":"itinerary.generation.succeeded","version":1,
                 "occurredAt":"2026-01-01T00:00:00Z","correlationId":"corr-1",
                 "requestId":"%s","tripId":"%s","summary":"A lovely trip","totalEstimatedCost":250.00,
                 "currency":"USD","days":[{"dayNumber":1,"date":"2026-06-01","theme":"Arrival","items":[
                   {"title":"Airport transfer","itemType":"TRANSPORT","estimatedCost":30.00}
                 ]}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), tripId);

        producer.send(new ProducerRecord<>("itinerary.generation.succeeded", tripId.toString(), message))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Integer activeCount = jdbcTemplate.queryForObject(
                    "select count(*) from itineraries where trip_id = ?::uuid and is_active = true",
                    Integer.class, tripId.toString());
            assertThat(activeCount).isEqualTo(1);
        });

        String status = jdbcTemplate.queryForObject(
                "select status from trips where id = ?::uuid", String.class, tripId.toString());
        assertThat(status).isEqualTo("READY");

        Integer itemCount = jdbcTemplate.queryForObject("""
                select count(*) from itinerary_items ii
                join itinerary_days d on ii.itinerary_day_id = d.id
                join itineraries i on d.itinerary_id = i.id
                where i.trip_id = ?::uuid
                """, Integer.class, tripId.toString());
        assertThat(itemCount).isEqualTo(1);
    }

    @Test
    void failedEvent_setsTripDraft_leavesExistingItinerariesUntouched() throws Exception {
        producer = rawProducer();
        UUID tripId = insertDraftTrip();
        jdbcTemplate.update("update trips set status = 'GENERATING' where id = ?::uuid", tripId.toString());

        String message = """
                {"eventId":"%s","eventType":"itinerary.generation.failed","version":1,
                 "occurredAt":"2026-01-01T00:00:00Z","correlationId":"corr-2",
                 "requestId":"%s","tripId":"%s","errorCode":"LLM_TIMEOUT","retryable":true}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), tripId);

        producer.send(new ProducerRecord<>("itinerary.generation.failed", tripId.toString(), message))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "select status from trips where id = ?::uuid", String.class, tripId.toString());
            assertThat(status).isEqualTo("DRAFT");
        });

        Integer itineraryCount = jdbcTemplate.queryForObject(
                "select count(*) from itineraries where trip_id = ?::uuid", Integer.class, tripId.toString());
        assertThat(itineraryCount).isZero();
    }

    private UUID insertDraftTrip() {
        UUID tripId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into trips (id, user_id, title, start_date, end_date, traveler_count, status)
                values (?::uuid, ?::uuid, ?, ?, ?, ?, 'DRAFT')
                """, tripId.toString(), UUID.randomUUID().toString(), "Saga test trip",
                java.sql.Date.valueOf("2026-06-01"), java.sql.Date.valueOf("2026-06-01"), 1);
        return tripId;
    }

    private KafkaProducer<String, String> rawProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }
}
