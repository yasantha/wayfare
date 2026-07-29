package com.wayfare.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The money demo, proven end-to-end: {@code POST .../itinerary:generate} in
 * DEMO_MODE returns 202 instantly; the async worker grounds against real
 * Catalog/User data (stubbed here), builds a valid itinerary with zero LLM
 * cost, and the {@code itinerary.generation.succeeded} event lands on a real
 * Kafka topic in the exact shape Trip Service's consumer (built in Phase 4)
 * already expects. Trip Service itself is deliberately NOT stubbed, proving
 * the best-effort {@code TripClient.markGenerating} call degrades gracefully
 * (design §3.4) rather than failing the whole generation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class GenerationDemoModeIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    static HttpServer catalogStub;
    static HttpServer userStub;
    static UUID destinationId = UUID.randomUUID();

    @BeforeAll
    static void startStubs() throws IOException {
        catalogStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        catalogStub.createContext("/destinations/" + destinationId, exchange ->
                respond(exchange, """
                        {"id":"%s","name":"Kyoto"}
                        """.formatted(destinationId)));
        catalogStub.createContext("/internal/activities/shortlist", exchange -> {
            String body = """
                    [{"id":"%s","name":"Fushimi Inari Shrine","category":"culture",
                      "estimatedCostUsd":0,"estimatedDurationMinutes":120},
                     {"id":"%s","name":"Arashiyama Bamboo Grove","category":"nature",
                      "estimatedCostUsd":0,"estimatedDurationMinutes":90}]
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());
            respond(exchange, body);
        });
        catalogStub.start();

        userStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        userStub.createContext("/internal/users/", exchange -> respond(exchange, """
                {"travelStyle":"adventure","pace":"relaxed","interests":["culture"],"avoidTags":[]}
                """));
        userStub.start();
    }

    @AfterAll
    static void stopStubs() {
        catalogStub.stop(0);
        userStub.stop(0);
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
        registry.add("llm.demo-mode", () -> "true");
        registry.add("wayfare.outbox.poll-interval-ms", () -> "200");
        registry.add("wayfare.catalog.base-url", () -> "http://localhost:" + catalogStub.getAddress().getPort());
        registry.add("wayfare.user.base-url", () -> "http://localhost:" + userStub.getAddress().getPort());
        // wayfare.trip.base-url deliberately left at its unreachable default —
        // proves markGenerating's failure is non-fatal.
    }

    @Autowired
    MockMvc mvc;

    KafkaConsumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void demoModeGeneration_completesAndPublishesSucceededEvent() throws Exception {
        consumer = rawConsumer("itinerary.generation.succeeded");

        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        String body = """
                {"destinationId":"%s","startDate":"2026-08-01","endDate":"2026-08-03",
                 "travelerCount":2,"budgetAmount":1000.00,"budgetCurrency":"USD"}
                """.formatted(destinationId);

        String response = mvc.perform(post("/trips/{id}/itinerary:generate", tripId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String requestId = extractId(response);

        ConsumerRecord<String, String> record = pollUntilFound(consumer, requestId, tripId.toString());

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode event = objectMapper.readTree(record.value());
        assertThat(event.path("eventType").asText()).isEqualTo("itinerary.generation.succeeded");
        assertThat(event.path("tripId").asText()).isEqualTo(tripId.toString());
        assertThat(event.path("requestId").asText()).isEqualTo(requestId);
        assertThat(event.path("days")).isNotEmpty();
        assertThat(event.path("days").get(0).path("items")).isNotEmpty();

        // Confirms the Catalog shortlist was actually used for grounding, not
        // silently dropped to an empty list (see CatalogClient.fetchShortlist).
        boolean anyItemGrounded = java.util.stream.StreamSupport.stream(event.path("days").spliterator(), false)
                .flatMap(day -> java.util.stream.StreamSupport.stream(day.path("items").spliterator(), false))
                .anyMatch(item -> item.hasNonNull("activityId"));
        assertThat(anyItemGrounded).as("at least one item should reference a real catalog activityId").isTrue();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String statusResponse = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/generation-requests/{id}", requestId)
                            .with(jwt().jwt(j -> j.subject(userId.toString()))))
                    .andReturn().getResponse().getContentAsString();
            JsonNode statusNode = objectMapper.readTree(statusResponse);
            assertThat(statusNode.path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(statusNode.path("model").asText()).isEqualTo("demo-mode");
        });
    }

    @Test
    void identicalRequestWithinWindow_dedupesToSameRequestId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        String body = """
                {"destinationId":"%s","startDate":"2026-09-01","endDate":"2026-09-02",
                 "travelerCount":1,"budgetAmount":500.00,"budgetCurrency":"USD"}
                """.formatted(destinationId);

        String first = mvc.perform(post("/trips/{id}/itinerary:generate", tripId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(post("/trips/{id}/itinerary:generate", tripId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(extractId(first)).isEqualTo(extractId(second));
    }

    private static ConsumerRecord<String, String> pollUntilFound(KafkaConsumer<String, String> consumer,
                                                                  String requestId, String tripId) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records) {
                if (r.value().contains(requestId) || r.value().contains(tripId)) {
                    return r;
                }
            }
        }
        throw new AssertionError("No succeeded event found for request " + requestId + " within timeout");
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

    private static String extractId(String json) {
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(json);
        if (!m.find()) throw new AssertionError("No id found in: " + json);
        return m.group(1);
    }
}
