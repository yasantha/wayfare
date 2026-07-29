package com.wayfare.trip.api.dto;

import com.wayfare.trip.domain.Itinerary;
import com.wayfare.trip.domain.ItinerarySource;

import java.math.BigDecimal;
import java.util.UUID;

public record ItineraryView(
        UUID id,
        UUID tripId,
        int version,
        ItinerarySource source,
        String summary,
        BigDecimal totalEstimatedCost,
        String currency,
        boolean active
) {
    public static ItineraryView from(Itinerary i) {
        return new ItineraryView(i.getId(), i.getTripId(), i.getVersion(), i.getSource(),
                i.getSummary(), i.getTotalEstimatedCost(), i.getCurrency(), i.isActive());
    }
}
