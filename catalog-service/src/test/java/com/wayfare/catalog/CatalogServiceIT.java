package com.wayfare.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog Service against real PostgreSQL (Flyway runs V1 schema + V2 seed data)
 * and a real Redis (for the @Cacheable destination/activity lookups).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CatalogServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    MockMvc mvc;

    @Test
    void seedData_loadedBySeedMigration() throws Exception {
        mvc.perform(get("/destinations").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(16));
    }

    @Test
    void search_filtersByQueryAndCountry() throws Exception {
        mvc.perform(get("/destinations").param("q", "Kyoto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Kyoto"));

        mvc.perform(get("/destinations").param("countryCode", "JP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void activitiesForDestination_returnsAround20() throws Exception {
        String body = mvc.perform(get("/destinations").param("q", "Kyoto"))
                .andReturn().getResponse().getContentAsString();
        String kyotoId = body.split("\"id\":\"")[1].split("\"")[0];

        mvc.perform(get("/destinations/{id}/activities", kyotoId).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(20));
    }

    @Test
    void unknownDestination_is404() throws Exception {
        mvc.perform(get("/destinations/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalShortlist_groundsPromptWithRealActivities() throws Exception {
        String body = mvc.perform(get("/destinations").param("q", "Bali"))
                .andReturn().getResponse().getContentAsString();
        String baliId = body.split("\"id\":\"")[1].split("\"")[0];

        String reqJson = "{\"destinationId\":\"" + baliId + "\",\"limit\":10}";
        mvc.perform(post("/internal/activities/shortlist")
                        .contentType("application/json").content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].destinationId").value(baliId));
    }
}
