package com.wayfare.trip.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** All fields optional; only non-null values are applied (partial update). */
public record UpdateTripRequest(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        Integer travelerCount,
        BigDecimal budgetAmount,
        String budgetCurrency
) {
}
