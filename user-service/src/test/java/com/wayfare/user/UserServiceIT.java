package com.wayfare.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User Service against real PostgreSQL. Uses the spring-security-test jwt()
 * post-processor to simulate a validated token, so no Auth Service is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false"); // no broker in this test
    }

    @Autowired
    MockMvc mvc;

    @Test
    void me_isCreatedLazily_andScopedToTheTokenSubject() throws Exception {
        String userId = UUID.randomUUID().toString();

        mvc.perform(get("/me").with(jwt().jwt(j -> j.subject(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.userId").value(userId));
    }

    @Test
    void putPreferences_persistsAndBumpsVersion() throws Exception {
        String userId = UUID.randomUUID().toString();
        String body = """
                {"travelStyle":"adventure","pace":"relaxed",
                 "interests":["hiking","food"],"avoidTags":["nightlife"]}
                """;

        mvc.perform(put("/me/preferences").with(jwt().jwt(j -> j.subject(userId)))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelStyle").value("adventure"))
                .andExpect(jsonPath("$.interests[0]").value("hiking"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/me/preferences").with(jwt().jwt(j -> j.subject(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avoidTags[0]").value("nightlife"));
    }

    @Test
    void me_withoutToken_is401() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void internalPreferences_reachableWithoutUserToken() throws Exception {
        mvc.perform(get("/internal/users/{id}/preferences", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
