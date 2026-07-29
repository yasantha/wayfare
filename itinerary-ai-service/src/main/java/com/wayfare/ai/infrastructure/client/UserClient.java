package com.wayfare.ai.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/** Sync call to User Service for preferences grounding (design §3.1, §7.3). */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestClient restClient;

    public UserClient(@Value("${wayfare.user.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(2000);
                    setReadTimeout(3000);
                }})
                .build();
    }

    public PreferencesView fetchPreferences(UUID userId) {
        try {
            PreferencesView p = restClient.get()
                    .uri("/internal/users/{id}/preferences", userId)
                    .retrieve()
                    .body(PreferencesView.class);
            return p != null ? p : PreferencesView.empty();
        } catch (Exception e) {
            // Degraded, not failed (design §3.4): generation proceeds without preferences.
            log.warn("Failed to fetch preferences for user {}: {}", userId, e.getMessage());
            return PreferencesView.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreferencesView(
            String travelStyle, String pace, List<String> interests, List<String> avoidTags
    ) {
        public static PreferencesView empty() {
            return new PreferencesView(null, null, List.of(), List.of());
        }
    }
}
