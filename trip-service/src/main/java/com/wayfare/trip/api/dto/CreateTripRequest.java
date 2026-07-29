package com.wayfare.trip.api.dto;

import com.wayfare.trip.api.validation.DateRanged;
import com.wayfare.trip.api.validation.ValidDateRange;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@ValidDateRange
public record CreateTripRequest(
        @NotBlank String title,
        UUID destinationId,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(1) int travelerCount,
        @Positive BigDecimal budgetAmount,
        String budgetCurrency
) implements DateRanged {
}
