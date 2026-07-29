package com.wayfare.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wayfare.ai.infrastructure.client.CatalogClient.ActivityView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Demo mode is not static fixture text — it deterministically arranges real,
 * catalog-grounded activities (the same {@link GenerationContext} a live LLM
 * call would receive) into a schedule matching the exact structured-output
 * shape design §7.4 defines, so the same validator and the same Trip Service
 * consumer handle it identically. Zero cost, near-zero latency, and a
 * reviewer sees a coherent result in milliseconds instead of waiting on a
 * real model (portfolio-build-plan §4).
 */
@Service
public class DemoItineraryBuilder {

    private static final LocalTime[] SLOT_STARTS = {
            LocalTime.of(9, 0), LocalTime.of(13, 30), LocalTime.of(16, 30), LocalTime.of(19, 0)
    };
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);

    private final ObjectMapper objectMapper;

    public DemoItineraryBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(GenerationContext ctx) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("summary", "A %d-day trip to %s, arranged from real local activities."
                .formatted(ctx.dayCount(), ctx.destinationName()));
        root.put("currency", ctx.budgetCurrency() != null ? ctx.budgetCurrency() : "USD");

        ArrayNode days = root.putArray("days");
        List<ActivityView> pool = ctx.shortlist();
        BigDecimal total = BigDecimal.ZERO;
        int poolIndex = 0;

        for (int day = 1; day <= ctx.dayCount(); day++) {
            ObjectNode dayNode = days.addObject();
            dayNode.put("dayNumber", day);
            dayNode.put("date", ctx.startDate().plusDays(day - 1).toString());
            dayNode.put("theme", day == 1 ? "Arrival & first impressions"
                    : day == ctx.dayCount() ? "Farewell" : "Exploring " + ctx.destinationName());

            List<TimedItem> dayItems = new ArrayList<>();
            int slotsToday = day == 1 || day == ctx.dayCount() ? 2 : 3;

            for (int slot = 0; slot < slotsToday; slot++) {
                LocalTime start = SLOT_STARTS[Math.min(slot, SLOT_STARTS.length - 1)];
                if (!pool.isEmpty()) {
                    ActivityView a = pool.get(poolIndex % pool.size());
                    poolIndex++;
                    BigDecimal cost = a.estimatedCostUsd() != null ? a.estimatedCostUsd() : BigDecimal.ZERO;
                    total = total.add(cost);
                    dayItems.add(TimedItem.activity(start, a, cost));
                } else {
                    dayItems.add(TimedItem.freeTime(start, ctx.destinationName()));
                }
            }

            BigDecimal mealCost = BigDecimal.valueOf(15);
            total = total.add(mealCost);
            dayItems.add(TimedItem.meal(LUNCH_START, ctx.destinationName(), mealCost));

            dayItems.sort(Comparator.comparing(TimedItem::start));
            ArrayNode items = dayNode.putArray("items");
            for (TimedItem ti : dayItems) {
                items.add(ti.toNode(objectMapper));
            }
        }

        root.put("totalEstimatedCost", total.setScale(2, RoundingMode.HALF_UP));
        return root.toString();
    }

    private record TimedItem(LocalTime start, LocalTime end, String activityId, String title, String itemType,
                             String description, String locationName, BigDecimal cost) {

        static TimedItem activity(LocalTime start, ActivityView a, BigDecimal cost) {
            return new TimedItem(start, start.plusHours(2), a.id().toString(), a.name(), "ACTIVITY",
                    "Enjoy " + a.name() + " (" + a.category() + ").", a.name(), cost);
        }

        static TimedItem freeTime(LocalTime start, String destinationName) {
            return new TimedItem(start, start.plusHours(2), null, "Free time to explore " + destinationName,
                    "FREE_TIME", "Unstructured time — wander, rest, or discover something local.",
                    destinationName, BigDecimal.ZERO);
        }

        static TimedItem meal(LocalTime start, String destinationName, BigDecimal cost) {
            return new TimedItem(start, start.plusHours(1), null, "Local lunch", "MEAL",
                    "A meal at a well-reviewed local spot.", destinationName, cost);
        }

        ObjectNode toNode(ObjectMapper objectMapper) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("startTime", start.toString());
            item.put("endTime", end.toString());
            if (activityId != null) {
                item.put("activityId", activityId);
            } else {
                item.putNull("activityId");
            }
            item.put("title", title);
            item.put("itemType", itemType);
            item.put("description", description);
            item.put("locationName", locationName);
            item.put("estimatedCost", cost);
            return item;
        }
    }
}
