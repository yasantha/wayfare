package com.wayfare.trip.api.dto;

import com.wayfare.trip.domain.ItineraryDay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ItineraryDayView(
        UUID id,
        int dayNumber,
        LocalDate date,
        String theme,
        String notes,
        BigDecimal estimatedCost
) {
    public static ItineraryDayView from(ItineraryDay d) {
        return new ItineraryDayView(d.getId(), d.getDayNumber(), d.getDate(), d.getTheme(),
                d.getNotes(), d.getEstimatedCost());
    }
}
