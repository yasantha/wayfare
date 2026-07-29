package com.wayfare.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wayfare.ai.infrastructure.client.CatalogClient.ActivityView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nothing goes into an event before passing here (design §7.5). Two distinct
 * behaviors: unknown {@code activityId} references are silently downgraded to
 * free-text items (not a validation failure — the model gets no repair
 * round-trip for this, since there is nothing for it to fix); everything else
 * failing is a real validation error that DOES trigger one repair round-trip.
 */
@Service
public class ItineraryValidator {

    private static final int MAX_ITEMS_PER_DAY = 10;
    private static final BigDecimal BUDGET_TOLERANCE_MULTIPLIER = BigDecimal.valueOf(1.5);

    public ValidationResult validate(ObjectNode itinerary, GenerationContext ctx) {
        reconcileUnknownActivityIds(itinerary, ctx.shortlist());

        List<String> errors = new ArrayList<>();
        validateDays(itinerary, ctx, errors);
        validateBudget(itinerary, ctx, errors);

        return new ValidationResult(errors.isEmpty(), errors, itinerary);
    }

    /** Unknown ids are downgraded, not rejected — this is a silent correction, not an error. */
    private void reconcileUnknownActivityIds(ObjectNode itinerary, List<ActivityView> shortlist) {
        Set<String> knownIds = shortlist.stream().map(a -> a.id().toString()).collect(Collectors.toSet());
        for (JsonNode day : itinerary.path("days")) {
            for (JsonNode item : day.path("items")) {
                JsonNode activityId = item.get("activityId");
                if (activityId != null && !activityId.isNull() && !knownIds.contains(activityId.asText())) {
                    ((ObjectNode) item).putNull("activityId");
                }
            }
        }
    }

    private void validateDays(ObjectNode itinerary, GenerationContext ctx, List<String> errors) {
        ArrayNode days = (ArrayNode) itinerary.path("days");
        long expected = ctx.dayCount();

        if (days.isEmpty()) {
            errors.add("Itinerary has no days");
            return;
        }
        if (days.size() != expected) {
            errors.add("Expected %d days, got %d".formatted(expected, days.size()));
        }

        Set<Integer> seenDayNumbers = new HashSet<>();
        for (JsonNode day : days) {
            int dayNumber = day.path("dayNumber").asInt(-1);
            if (dayNumber < 1 || dayNumber > expected) {
                errors.add("Day number out of range: " + dayNumber);
            }
            if (!seenDayNumbers.add(dayNumber)) {
                errors.add("Duplicate day number: " + dayNumber);
            }

            ArrayNode items = (ArrayNode) day.path("items");
            if (items.isEmpty()) {
                errors.add("Day %d has no items".formatted(dayNumber));
            }
            if (items.size() > MAX_ITEMS_PER_DAY) {
                errors.add("Day %d has an unreasonable number of items (%d)".formatted(dayNumber, items.size()));
            }

            validateTimeOrdering(items, dayNumber, errors);
        }
    }

    private void validateTimeOrdering(ArrayNode items, int dayNumber, List<String> errors) {
        LocalTime previousEnd = null;
        for (JsonNode item : items) {
            JsonNode startNode = item.get("startTime");
            JsonNode endNode = item.get("endTime");
            if (startNode == null || startNode.isNull() || endNode == null || endNode.isNull()) {
                continue; // untimed items (e.g. FREE_TIME) don't participate in ordering checks
            }
            LocalTime start;
            LocalTime end;
            try {
                start = LocalTime.parse(startNode.asText());
                end = LocalTime.parse(endNode.asText());
            } catch (Exception e) {
                errors.add("Day %d has an unparseable time".formatted(dayNumber));
                continue;
            }
            if (!end.isAfter(start)) {
                errors.add("Day %d has an item ending before it starts".formatted(dayNumber));
            }
            if (previousEnd != null && start.isBefore(previousEnd)) {
                errors.add("Day %d has overlapping items".formatted(dayNumber));
            }
            previousEnd = end;
        }
    }

    private void validateBudget(ObjectNode itinerary, GenerationContext ctx, List<String> errors) {
        if (ctx.budgetAmount() == null) {
            return;
        }
        JsonNode totalNode = itinerary.get("totalEstimatedCost");
        if (totalNode == null || totalNode.isNull()) {
            return;
        }
        BigDecimal total = new BigDecimal(totalNode.asText("0"));
        BigDecimal ceiling = ctx.budgetAmount().multiply(BUDGET_TOLERANCE_MULTIPLIER);
        if (total.compareTo(ceiling) > 0) {
            errors.add("Total estimated cost %s exceeds tolerance for budget %s".formatted(total, ctx.budgetAmount()));
        }
    }

    public record ValidationResult(boolean valid, List<String> errors, ObjectNode reconciled) {
    }
}
