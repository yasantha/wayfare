package com.wayfare.trip.api.dto;

import com.wayfare.trip.domain.Trip;
import com.wayfare.trip.domain.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TripView(
        UUID id,
        String title,
        UUID destinationId,
        LocalDate startDate,
        LocalDate endDate,
        int travelerCount,
        BigDecimal budgetAmount,
        String budgetCurrency,
        TripStatus status
) {
    public static TripView from(Trip t) {
        return new TripView(t.getId(), t.getTitle(), t.getDestinationId(), t.getStartDate(), t.getEndDate(),
                t.getTravelerCount(), t.getBudgetAmount(), t.getBudgetCurrency(), t.getStatus());
    }
}
