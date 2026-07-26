package com.wayfare.catalog.application;

import com.wayfare.catalog.domain.Activity;
import com.wayfare.catalog.domain.Destination;
import com.wayfare.catalog.repository.ActivityRepository;
import com.wayfare.catalog.repository.DestinationRepository;
import com.wayfare.commons.error.Exceptions.ResourceNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Destinations and activities: near-static, read-heavy reference data, cached
 * in Redis (design §4.1/§9.1). Writes (seeding/curation) go through the
 * repositories directly and are not exposed as a public write API in the MVP.
 */
@Service
public class CatalogService {

    private final DestinationRepository destinations;
    private final ActivityRepository activities;

    public CatalogService(DestinationRepository destinations, ActivityRepository activities) {
        this.destinations = destinations;
        this.activities = activities;
    }

    public Page<Destination> searchDestinations(String query, String countryCode, Pageable pageable) {
        return destinations.search(blankToNull(query), blankToNull(countryCode), pageable);
    }

    @Cacheable(cacheNames = "destination", key = "#id")
    public Destination getDestination(UUID id) {
        return destinations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found: " + id));
    }

    public Page<Activity> getActivitiesForDestination(UUID destinationId, Pageable pageable) {
        // Ensure the destination exists (404 rather than a silently empty page).
        getDestination(destinationId);
        return activities.findByDestinationIdAndActiveTrue(destinationId, pageable);
    }

    @Cacheable(cacheNames = "activity", key = "#id")
    public Activity getActivity(UUID id) {
        return activities.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found: " + id));
    }

    /**
     * Prompt-grounding shortlist for Itinerary AI (design §7.3): up to {@code limit}
     * real activities for a destination, optionally capped by budget, best-rated first.
     */
    public List<Activity> shortlist(UUID destinationId, BigDecimal maxCost, int limit) {
        return activities.shortlist(destinationId, maxCost, Math.min(limit, 60));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
