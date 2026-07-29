package com.wayfare.reco;

import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Projection consumers + scoring endpoints against real Postgres and Kafka.
 * Catalog is stubbed (design §3.1's one sync dependency); User Service is
 * never called — the interest profile comes entirely from consumed events
 * (design §4.3), proven here by never starting a User Service at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class RecommendationServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    static HttpServer catalogStub;
    static UUID kyotoId = UUID.randomUUID();
    static UUID beachDestinationId = UUID.randomUUID();
    static UUID shrineActivityId = UUID.randomUUID();
    static UUID busyClubActivityId = UUID.randomUUID();

    @BeforeAll
    static void startCatalogStub() throws IOException {
        catalogStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        catalogStub.createContext("/destinations", exchange -> {
            if (!"/destinations".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String body = """
                    {"content":[
                      {"id":"%s","name":"Kyoto","countryCode":"JP","bestMonths":[3,4],
                       "avgDailyCostUsd":100.00,"popularityScore":90.00,"tags":["culture","history"]},
                      {"id":"%s","name":"Beach Town","countryCode":"XX","bestMonths":[7,8],
                       "avgDailyCostUsd":50.00,"popularityScore":40.00,"tags":["beach","nightlife"]}
                    ]}
                    """.formatted(kyotoId, beachDestinationId);
            respond(exchange, body);
        });
        catalogStub.createContext("/destinations/" + kyotoId + "/activities", exchange -> {
            String body = """
                    {"content":[
                      {"id":"%s","destinationId":"%s","name":"Fushimi Inari Shrine",
                       "estimatedCostUsd":0,"tags":["culture"],"rating":4.7},
                      {"id":"%s","destinationId":"%s","name":"Busy Club",
                       "estimatedCostUsd":20,"tags":["nightlife","crowded"],"rating":4.0}
                    ]}
                    """.formatted(shrineActivityId, kyotoId, busyClubActivityId, kyotoId);
            respond(exchange, body);
        });
        catalogStub.start();
    }

    @AfterAll
    static void stopCatalogStub() {
        catalogStub.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("wayfare.catalog.base-url", () -> "http://localhost:" + catalogStub.getAddress().getPort());
    }

    @Autowired
    MockMvc mvc;

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
    void preferencesUpdated_projectsInterests_andScoresDestinationsAccordingly() throws Exception {
        producer = rawProducer();
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String message = """
                {"eventId":"%s","eventType":"user.preferences.updated","version":1,
                 "occurredAt":"2026-01-01T00:00:00Z","correlationId":"corr-1",
                 "userId":"%s","interests":["culture","history"],"avoidTags":["crowded"]}
                """.formatted(eventId, userId);

        producer.send(new ProducerRecord<>("user.preferences.updated", userId.toString(), message))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from user_interest_profiles where user_id = ?::uuid", Integer.class, userId);
            assertThat(count).isEqualTo(1);
        });

        mvc.perform(get("/recommendations/destinations").param("travelMonth", "3")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kyoto")); // culture/history match beats beach/nightlife
    }

    @Test
    void tripCreated_projectsVisitedDestinationAndBudgetTier() throws Exception {
        producer = rawProducer();
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String message = """
                {"eventId":"%s","eventType":"trip.created","version":1,
                 "occurredAt":"2026-01-01T00:00:00Z","correlationId":"corr-2",
                 "tripId":"%s","userId":"%s","destinationId":"%s",
                 "startDate":"2026-06-01","endDate":"2026-06-05",
                 "budgetAmount":500.00,"budgetCurrency":"USD"}
                """.formatted(eventId, UUID.randomUUID(), userId, kyotoId);

        producer.send(new ProducerRecord<>("trip.created", userId.toString(), message)).get(10, TimeUnit.SECONDS);

        // queryForList (not queryForObject): the row won't exist yet on early polls,
        // and queryForObject throws EmptyResultDataAccessException in that case — a
        // plain RuntimeException, not an AssertionError, so Awaitility's untilAsserted
        // (which only retries on assertion failures) would fail fast instead of polling.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            java.util.List<String> rows = jdbcTemplate.queryForList(
                    "select visited_destination_ids::text from user_interest_profiles where user_id = ?::uuid",
                    String.class, userId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).contains(kyotoId.toString());
        });

        // 500 / 5 days = 100.00 per day
        java.math.BigDecimal tier = jdbcTemplate.queryForObject(
                "select avg_budget_tier from user_interest_profiles where user_id = ?::uuid",
                java.math.BigDecimal.class, userId);
        assertThat(tier).isEqualByComparingTo("100.00");
    }

    @Test
    void activityRecommendations_excludeGivenIds_andPenalizeAvoidedTags() throws Exception {
        UUID userId = UUID.randomUUID();

        String response = mvc.perform(get("/trips/{id}/recommendations/activities", UUID.randomUUID())
                        .param("destinationId", kyotoId.toString())
                        .param("excludeActivityIds", shrineActivityId.toString())
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(shrineActivityId.toString());
        assertThat(response).contains(busyClubActivityId.toString());
    }

    private KafkaProducer<String, String> rawProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(props);
    }
}
