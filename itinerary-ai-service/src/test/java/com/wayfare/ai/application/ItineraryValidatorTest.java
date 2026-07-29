package com.wayfare.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wayfare.ai.infrastructure.client.CatalogClient.ActivityView;
import com.wayfare.ai.infrastructure.client.UserClient.PreferencesView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ItineraryValidator validator = new ItineraryValidator();
    private final UUID knownActivityId = UUID.randomUUID();

    private GenerationContext context(int days, BigDecimal budget) {
        return new GenerationContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Kyoto",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(days - 1), 2, budget, "USD",
                PreferencesView.empty(),
                List.of(new ActivityView(knownActivityId, "Fushimi Inari", "culture",
                        BigDecimal.TEN, 90)));
    }

    private ObjectNode parse(String json) throws Exception {
        return (ObjectNode) objectMapper.readTree(json);
    }

    @Test
    void validItinerary_passes() throws Exception {
        String json = """
                {"summary":"A trip","totalEstimatedCost":50,"currency":"USD","days":[
                  {"dayNumber":1,"date":"2026-01-01","theme":"Arrival","items":[
                    {"activityId":"%s","title":"Fushimi Inari","itemType":"ACTIVITY",
                     "startTime":"09:00","endTime":"11:00","estimatedCost":10}
                  ]}
                ]}
                """.formatted(knownActivityId);

        var result = validator.validate(parse(json), context(1, BigDecimal.valueOf(100)));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void dayCountMismatch_fails() throws Exception {
        String json = """
                {"summary":"s","totalEstimatedCost":0,"currency":"USD","days":[
                  {"dayNumber":1,"date":"2026-01-01","theme":"t","items":[
                    {"activityId":null,"title":"x","itemType":"FREE_TIME","estimatedCost":0}
                  ]}
                ]}
                """;

        var result = validator.validate(parse(json), context(3, null)); // trip is 3 days, itinerary has 1

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Expected 3 days"));
    }

    @Test
    void emptyDay_fails() throws Exception {
        String json = """
                {"summary":"s","totalEstimatedCost":0,"currency":"USD","days":[
                  {"dayNumber":1,"date":"2026-01-01","theme":"t","items":[]}
                ]}
                """;

        var result = validator.validate(parse(json), context(1, null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("no items"));
    }

    @Test
    void overlappingItems_fail() throws Exception {
        String json = """
                {"summary":"s","totalEstimatedCost":0,"currency":"USD","days":[
                  {"dayNumber":1,"date":"2026-01-01","theme":"t","items":[
                    {"activityId":null,"title":"a","itemType":"ACTIVITY","startTime":"09:00","endTime":"11:00","estimatedCost":0},
                    {"activityId":null,"title":"b","itemType":"ACTIVITY","startTime":"10:00","endTime":"12:00","estimatedCost":0}
                  ]}
                ]}
                """;

        var result = validator.validate(parse(json), context(1, null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("overlapping"));
    }

    @Test
    void unknownActivityId_isDowngradedSilently_notAnError() throws Exception {
        UUID unknownId = UUID.randomUUID();
        String json = """
                {"summary":"s","totalEstimatedCost":0,"currency":"USD","days":[
                  {"dayNumber":1,"date":"2026-01-01","theme":"t","items":[
                    {"activityId":"%s","title":"Mystery spot","itemType":"ACTIVITY",
                     "startTime":"09:00","endTime":"11:00","estimatedCost":0}
                  ]}
                ]}
                """.formatted(unknownId);

        var result = validator.validate(parse(json), context(1, null));

        assertThat(result.valid()).isTrue();
        assertThat(result.reconciled().path("days").get(0).path("items").get(0).path("activityId").isNull())
                .isTrue();
    }

    @Test
    void costFarBeyondBudget_fails() throws Exception {
        String json = """
                {"summary":"s","totalEstimatedCost":1000,"currency":"USD","days":[
                  {"dayNumber":1,"date":"2026-01-01","theme":"t","items":[
                    {"activityId":null,"title":"a","itemType":"ACTIVITY","startTime":"09:00","endTime":"11:00","estimatedCost":1000}
                  ]}
                ]}
                """;

        var result = validator.validate(parse(json), context(1, BigDecimal.valueOf(100)));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("exceeds tolerance"));
    }
}
