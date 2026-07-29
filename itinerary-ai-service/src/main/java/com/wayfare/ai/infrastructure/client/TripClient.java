package com.wayfare.ai.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Sync call to Trip Service's internal status endpoint (design §4.4 saga step
 * 1). Best-effort: a failure here only means the trip's UI doesn't show
 * "generating" promptly — it must never block or fail the generation itself,
 * since the authoritative outcome is always the succeeded/failed event Trip
 * already consumes.
 */
@Component
public class TripClient {

    private static final Logger log = LoggerFactory.getLogger(TripClient.class);

    private final RestClient restClient;

    public TripClient(@Value("${wayfare.trip.base-url:http://localhost:8084}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(2000);
                    setReadTimeout(3000);
                }})
                .build();
    }

    public void markGenerating(UUID tripId) {
        try {
            restClient.patch()
                    .uri("/internal/trips/{id}/status", tripId)
                    .body(Map.of("status", "GENERATING"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to mark trip {} as GENERATING (non-fatal): {}", tripId, e.getMessage());
        }
    }
}
