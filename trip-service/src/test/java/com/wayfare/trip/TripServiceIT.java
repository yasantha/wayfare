package com.wayfare.trip;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trip CRUD, itinerary versioning, days/items, reordering, and ownership
 * enforcement against real PostgreSQL. A tiny JDK HttpServer stubs Catalog's
 * {@code GET /destinations/{id}} so the destination-snapshot path is exercised
 * without needing catalog-service running (a different Maven module/JVM).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TripServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static HttpServer catalogStub;
    static UUID stubDestinationId = UUID.randomUUID();

    @BeforeAll
    static void startCatalogStub() throws IOException {
        catalogStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        catalogStub.createContext("/destinations/" + stubDestinationId, exchange -> {
            String body = """
                    {"id":"%s","name":"Kyoto","countryCode":"JP","latitude":35.0116,
                     "longitude":135.7681,"avgDailyCostUsd":110.00}
                    """.formatted(stubDestinationId);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        catalogStub.start();
    }

    @AfterAll
    static void stopCatalogStub() {
        catalogStub.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false"); // no broker in this test
        registry.add("wayfare.catalog.base-url",
                () -> "http://localhost:" + (catalogStub != null ? catalogStub.getAddress().getPort() : 0));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** ISO date N days from today — avoids hardcoded literals that drift into the past over a long session. */
    private static String future(int daysFromNow) {
        return java.time.LocalDate.now().plusDays(daysFromNow).toString();
    }

    private static String tripBody(String title, UUID destinationId, String start, String end) {
        return """
                {"title":"%s","destinationId":%s,"startDate":"%s","endDate":"%s",
                 "travelerCount":2,"budgetAmount":1500.00,"budgetCurrency":"USD"}
                """.formatted(title, destinationId == null ? "null" : "\"" + destinationId + "\"", start, end);
    }

    @Test
    void createTrip_withDestination_snapshotsFromCatalog() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = tripBody("Kyoto adventure", stubDestinationId, future(30), future(34));

        String response = mvc.perform(post("/trips").with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Kyoto adventure"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String tripId = extractId(response);

        String snapshot = jdbcTemplate.queryForObject(
                "select destination_snapshot::text from trips where id = ?::uuid", String.class, tripId);
        org.assertj.core.api.Assertions.assertThat(snapshot).contains("Kyoto").contains("110.0");
    }

    @Test
    void ownership_secondUserCannotAccessFirstUsersTrip() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String body = tripBody("Private trip", null, future(40), future(42));

        String response = mvc.perform(post("/trips").with(jwt().jwt(j -> j.subject(owner.toString())))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String tripId = extractId(response);

        mvc.perform(get("/trips/{id}", tripId).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk());
        mvc.perform(get("/trips/{id}", tripId).with(jwt().jwt(j -> j.subject(attacker.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDateRange_isRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        String body = tripBody("Backwards trip", null, future(50), future(45)); // end before start

        mvc.perform(post("/trips").with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullItineraryWorkflow_createEditReorderActivate() throws Exception {
        UUID userId = UUID.randomUUID();
        String tripBody = tripBody("Weekend trip", null, future(60), future(62)); // 3 days

        String tripResponse = mvc.perform(post("/trips").with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json").content(tripBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String tripId = extractId(tripResponse);

        // Create a manual itinerary version -> auto-seeded with 3 days
        String itineraryResponse = mvc.perform(post("/trips/{id}/itineraries", tripId)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String itineraryId = extractId(itineraryResponse);

        String detail = mvc.perform(get("/itineraries/{id}", itineraryId)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String dayId = extractFirst(detail, "\"days\":\\[\\{\"id\":\"([0-9a-f-]+)\"");

        // Add two items to day 1
        String item1Response = mvc.perform(post("/itineraries/{iid}/days/{did}/items", itineraryId, dayId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json")
                        .content("{\"title\":\"Fushimi Inari\",\"itemType\":\"ACTIVITY\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String item1Id = extractId(item1Response);

        String item2Response = mvc.perform(post("/itineraries/{iid}/days/{did}/items", itineraryId, dayId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json")
                        .content("{\"title\":\"Lunch\",\"itemType\":\"MEAL\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String item2Id = extractId(item2Response);

        // Reorder: item2 before item1
        mvc.perform(put("/itineraries/{iid}/days/{did}/items/order", itineraryId, dayId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json")
                        .content("{\"itemIds\":[\"" + item2Id + "\",\"" + item1Id + "\"]}"))
                .andExpect(status().isNoContent());

        // Update item1
        mvc.perform(patch("/itinerary-items/{id}", item1Id)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json")
                        .content("{\"title\":\"Fushimi Inari Shrine\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fushimi Inari Shrine"));

        // Delete item2
        mvc.perform(delete("/itinerary-items/{id}", item2Id)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isNoContent());

        // Activate -> trip status READY
        mvc.perform(post("/itineraries/{id}/activate", itineraryId)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(get("/trips/{id}", tripId).with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        // Delete the trip entirely
        mvc.perform(delete("/trips/{id}", tripId).with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/trips/{id}", tripId).with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isNotFound());
    }

    private static String extractId(String json) {
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(json);
        if (!m.find()) throw new AssertionError("No id found in: " + json);
        return m.group(1);
    }

    private static String extractFirst(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        if (!m.find()) throw new AssertionError("Pattern not found in: " + json);
        return m.group(1);
    }
}
