package com.wayfare.ai.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trip parameters carried directly on the request (design ADR-011) — the
 * caller already has these from Trip Service, and the design's own sequence
 * diagram never shows this service calling back to Trip for them.
 */
public record GenerateRequest(
        UUID destinationId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(1) int travelerCount,
        @Positive BigDecimal budgetAmount,
        String budgetCurrency
) {
}
