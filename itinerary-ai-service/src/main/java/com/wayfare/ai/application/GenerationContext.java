package com.wayfare.ai.application;

import com.wayfare.ai.infrastructure.client.CatalogClient.ActivityView;
import com.wayfare.ai.infrastructure.client.UserClient.PreferencesView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Everything the model (or the demo-mode algorithmic builder) needs to
 * produce a grounded itinerary (design §7.3): trip parameters, the
 * preferences snapshot, and a shortlist of real activities.
 */
public record GenerationContext(
        UUID tripId,
        UUID userId,
        UUID destinationId,
        String destinationName,
        LocalDate startDate,
        LocalDate endDate,
        int travelerCount,
        BigDecimal budgetAmount,
        String budgetCurrency,
        PreferencesView preferences,
        List<ActivityView> shortlist
) {
    public long dayCount() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
