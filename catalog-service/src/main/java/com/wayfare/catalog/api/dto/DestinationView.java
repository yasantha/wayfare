package com.wayfare.catalog.api.dto;

import com.wayfare.catalog.domain.Destination;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DestinationView(
        UUID id,
        String name,
        String countryCode,
        String region,
        double latitude,
        double longitude,
        String timezone,
        String description,
        List<Integer> bestMonths,
        BigDecimal avgDailyCostUsd,
        BigDecimal popularityScore,
        List<String> tags
) {
    public static DestinationView from(Destination d) {
        return new DestinationView(d.getId(), d.getName(), d.getCountryCode(), d.getRegion(),
                d.getLatitude(), d.getLongitude(), d.getTimezone(), d.getDescription(),
                d.getBestMonths(), d.getAvgDailyCostUsd(), d.getPopularityScore(), d.getTags());
    }
}
