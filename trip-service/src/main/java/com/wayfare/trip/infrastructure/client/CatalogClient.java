package com.wayfare.trip.infrastructure.client;

import com.wayfare.commons.error.Exceptions.ExternalServiceException;
import com.wayfare.commons.error.Exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sync call to Catalog Service (design §3.1: fast, and a stale answer would be
 * wrong — the criteria for choosing sync over async) to snapshot a destination
 * at trip-creation time. The snapshot is what makes Trip independent of Catalog
 * afterward (design §4.3): a later catalog edit never corrupts a saved trip.
 */
@Component
public class CatalogClient {

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

    public DestinationSnapshot fetchDestination(UUID destinationId) {
        try {
            return restClient.get()
                    .uri("/destinations/{id}", destinationId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ResourceNotFoundException("Destination not found: " + destinationId);
                    })
                    .body(DestinationSnapshot.class);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("Catalog Service unavailable", e);
        }
    }

    /** Subset of catalog fields worth freezing onto the trip (design §4.3). */
    public record DestinationSnapshot(
            UUID id,
            String name,
            String countryCode,
            double latitude,
            double longitude,
            BigDecimal avgDailyCostUsd
    ) {
    }
}
