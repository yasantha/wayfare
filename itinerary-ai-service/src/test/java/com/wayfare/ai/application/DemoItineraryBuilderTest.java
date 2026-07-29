package com.wayfare.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.ai.infrastructure.client.CatalogClient.ActivityView;
import com.wayfare.ai.infrastructure.client.UserClient.PreferencesView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DemoItineraryBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DemoItineraryBuilder builder = new DemoItineraryBuilder(objectMapper);
    private final ItineraryValidator validator = new ItineraryValidator();

    @Test
    void demoOutput_isValidAgainstTheRealValidator() throws Exception {
        GenerationContext ctx = new GenerationContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Bali", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4), 2,
                BigDecimal.valueOf(2000), "USD", PreferencesView.empty(),
                List.of(
                        new ActivityView(UUID.randomUUID(), "Beach day", "beach", BigDecimal.valueOf(20), 180),
                        new ActivityView(UUID.randomUUID(), "Temple tour", "culture", BigDecimal.valueOf(15), 120)
                ));

        String rawJson = builder.build(ctx);
        JsonNode parsed = objectMapper.readTree(rawJson);

        assertThat(parsed.path("days")).hasSize(4);

        var result = validator.validate((com.fasterxml.jackson.databind.node.ObjectNode) parsed, ctx);
        assertThat(result.valid()).as("errors: %s", result.errors()).isTrue();
    }

    @Test
    void demoOutput_withNoShortlist_stillProducesValidFreeTimeItinerary() throws Exception {
        GenerationContext ctx = new GenerationContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Nowhereville", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2), 1,
                null, "USD", PreferencesView.empty(), List.of());

        String rawJson = builder.build(ctx);
        JsonNode parsed = objectMapper.readTree(rawJson);

        var result = validator.validate((com.fasterxml.jackson.databind.node.ObjectNode) parsed, ctx);
        assertThat(result.valid()).as("errors: %s", result.errors()).isTrue();
    }
}
