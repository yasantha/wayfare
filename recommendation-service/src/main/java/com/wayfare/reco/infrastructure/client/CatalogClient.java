package com.wayfare.reco.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wayfare.commons.error.Exceptions.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Sync call to Catalog for scoring (design §3.1: "Recommendation -> Catalog:
 * Sync REST, cached" — the one sync dependency this service has; user
 * interest data comes entirely from the projection, never a live User call).
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

    public List<DestinationView> fetchDestinations(int limit) {
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/destinations").queryParam("size", limit).build())
                    .retrieve()
                    .body(DestinationPage.class);
            return response != null && response.content() != null ? response.content() : List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch destinations for scoring: {}", e.getMessage());
            return List.of();
        }
    }

    public List<ActivityView> fetchActivitiesForDestination(UUID destinationId, int limit) {
        try {
            var response = restClient.get()
                    .uri("/destinations/{id}/activities?size={limit}", destinationId, limit)
                    .retrieve()
                    .body(ActivityPage.class);
            return response != null && response.content() != null ? response.content() : List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch activities for destination {}: {}", destinationId, e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DestinationPage(List<DestinationView> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityPage(List<ActivityView> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DestinationView(
            UUID id, String name, String countryCode, List<Integer> bestMonths,
            BigDecimal avgDailyCostUsd, BigDecimal popularityScore, List<String> tags
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityView(
            UUID id, UUID destinationId, String name, BigDecimal estimatedCostUsd,
            List<String> tags, BigDecimal rating
    ) {
    }
}
