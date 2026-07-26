package com.wayfare.catalog.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** Bulk fetch for prompt grounding (design §7.3): 30-60 activities, budget-filtered. */
public record ShortlistRequest(
        @NotNull UUID destinationId,
        BigDecimal maxCostUsd,
        @Positive @Max(60) Integer limit
) {
}
