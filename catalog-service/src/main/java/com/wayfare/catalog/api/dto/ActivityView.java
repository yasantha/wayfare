package com.wayfare.catalog.api.dto;

import com.wayfare.catalog.domain.Activity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ActivityView(
        UUID id,
        UUID destinationId,
        String name,
        String category,
        String description,
        Double latitude,
        Double longitude,
        BigDecimal estimatedCostUsd,
        int estimatedDurationMinutes,
        List<String> tags,
        BigDecimal rating,
        boolean indoor,
        String bookingUrl
) {
    public static ActivityView from(Activity a) {
        return new ActivityView(a.getId(), a.getDestinationId(), a.getName(), a.getCategory(),
                a.getDescription(), a.getLatitude(), a.getLongitude(), a.getEstimatedCostUsd(),
                a.getEstimatedDurationMinutes(), a.getTags(), a.getRating(), a.isIndoor(), a.getBookingUrl());
    }
}
