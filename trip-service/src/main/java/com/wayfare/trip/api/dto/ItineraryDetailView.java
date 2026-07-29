package com.wayfare.trip.api.dto;

import com.wayfare.trip.domain.Itinerary;
import com.wayfare.trip.domain.ItinerarySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Full aggregate view (design Appendix B: days/items are only ever read as part of the itinerary). */
public record ItineraryDetailView(
        UUID id,
        UUID tripId,
        int version,
        ItinerarySource source,
        String summary,
        BigDecimal totalEstimatedCost,
        String currency,
        boolean active,
        List<DayDetail> days
) {
    public static ItineraryDetailView from(Itinerary i, List<DayDetail> days) {
        return new ItineraryDetailView(i.getId(), i.getTripId(), i.getVersion(), i.getSource(),
                i.getSummary(), i.getTotalEstimatedCost(), i.getCurrency(), i.isActive(), days);
    }

    public record DayDetail(
            UUID id,
            int dayNumber,
            java.time.LocalDate date,
            String theme,
            String notes,
            BigDecimal estimatedCost,
            List<ItineraryItemView> items
    ) {
    }
}
