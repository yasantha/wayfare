package com.wayfare.ai.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wayfare.commons.error.Exceptions.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sync calls to Catalog for prompt grounding (design §3.1, §7.3): a
 * destination name for readability, and a shortlist of real activities the
 * model is instructed to prefer over inventing venues.
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;

    public CatalogClient(@Value("${wayfare.catalog.base-url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(2000);
                    setReadTimeout(3000);
                }})
                .build();
    }

    public String fetchDestinationName(UUID destinationId) {
        try {
            DestinationView d = restClient.get()
                    .uri("/destinations/{id}", destinationId)
                    .retrieve()
                    .body(DestinationView.class);
            return d != null ? d.name() : "the destination";
        } catch (Exception e) {
            throw new ExternalServiceException("Catalog Service unavailable", e);
        }
    }

    public List<ActivityView> fetchShortlist(UUID destinationId, BigDecimal maxCostUsd, int limit) {
        try {
            // Map.of(...) throws NullPointerException on a null value — maxCostUsd is
            // optional, so a mutable map that simply omits it when absent is required.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("destinationId", destinationId);
            if (maxCostUsd != null) {
                body.put("maxCostUsd", maxCostUsd);
            }
            body.put("limit", limit);

            ActivityView[] activities = restClient.post()
                    .uri("/internal/activities/shortlist")
                    .body(body)
                    .retrieve()
                    .body(ActivityView[].class);
            return activities != null ? List.of(activities) : List.of();
        } catch (Exception e) {
            // Design §3.4: degraded, not failed — generation proceeds ungrounded rather
            // than failing outright when Catalog is unreachable. Logged so a real
            // outage (or bug) is still visible, not silently invisible.
            log.warn("Failed to fetch activity shortlist for destination {}: {}", destinationId, e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DestinationView(UUID id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityView(
            UUID id, String name, String category, BigDecimal estimatedCostUsd, int estimatedDurationMinutes
    ) {
    }
}
