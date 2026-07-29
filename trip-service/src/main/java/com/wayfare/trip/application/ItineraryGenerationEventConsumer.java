package com.wayfare.trip.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.trip.domain.Itinerary;
import com.wayfare.trip.domain.ItineraryDay;
import com.wayfare.trip.domain.ItineraryItem;
import com.wayfare.trip.domain.ItemType;
import com.wayfare.trip.domain.ItinerarySource;
import com.wayfare.trip.domain.ProcessedEvent;
import com.wayfare.trip.domain.TripStatus;
import com.wayfare.trip.repository.ItineraryDayRepository;
import com.wayfare.trip.repository.ItineraryItemRepository;
import com.wayfare.trip.repository.ItineraryRepository;
import com.wayfare.trip.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Consumer side of the generation saga (design §4.4, steps 3-4). Not yet
 * produced by anything — Itinerary AI Service is built in Phase 5 — but wired
 * and tested now against hand-built events, the same way {@code
 * UserRegisteredConsumer} was ready before the Phase 3 outbox poller existed.
 *
 * <p>On success: a brand-new itinerary VERSION is persisted and activated —
 * never an update to an existing version — so a partial/failed generation can
 * never damage the plan the user already has (design §4.4). On failure: the
 * trip drops back to DRAFT and the previously active itinerary is untouched.
 */
@Component
public class ItineraryGenerationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationEventConsumer.class);
    private static final String CONSUMER = "trip-service";

    private final TripService tripService;
    private final ItineraryRepository itineraries;
    private final ItineraryDayRepository days;
    private final ItineraryItemRepository items;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public ItineraryGenerationEventConsumer(TripService tripService, ItineraryRepository itineraries,
                                            ItineraryDayRepository days, ItineraryItemRepository items,
                                            ProcessedEventRepository processedEvents, ObjectMapper objectMapper) {
        this.tripService = tripService;
        this.itineraries = itineraries;
        this.days = days;
        this.items = items;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0),
            autoCreateTopics = "true", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "itinerary.generation.succeeded", groupId = CONSUMER)
    @Transactional
    public void onSucceeded(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventId = event.path("eventId").asText(null);
        if (alreadyProcessed(eventId)) {
            return;
        }

        UUID tripId = UUID.fromString(event.path("tripId").asText());
        UUID requestId = event.hasNonNull("requestId") ? UUID.fromString(event.path("requestId").asText()) : null;

        int nextVersion = itineraries.findMaxVersion(tripId) + 1;
        Itinerary itinerary = Itinerary.create(tripId, nextVersion, ItinerarySource.AI,
                event.path("currency").asText(null));
        itinerary.setSummary(event.path("summary").asText(null));
        if (event.hasNonNull("totalEstimatedCost")) {
            itinerary.setTotalEstimatedCost(new BigDecimal(event.path("totalEstimatedCost").asText()));
        }
        itinerary.setGenerationRequestId(requestId);
        itineraries.save(itinerary);

        for (JsonNode dayNode : event.path("days")) {
            ItineraryDay day = ItineraryDay.create(itinerary.getId(),
                    dayNode.path("dayNumber").asInt(),
                    LocalDate.parse(dayNode.path("date").asText()));
            day.setTheme(dayNode.path("theme").asText(null));
            days.save(day);

            int order = 0;
            for (JsonNode itemNode : dayNode.path("items")) {
                items.save(itemFrom(day.getId(), order++, itemNode));
            }
        }

        itineraries.deactivateAllForTrip(tripId);
        itinerary.setActive(true);
        itineraries.save(itinerary);
        tripService.setStatus(tripId, TripStatus.READY);

        markProcessed(eventId);
        log.info("Persisted AI itinerary v{} for trip {} ({})", nextVersion, tripId, requestId);
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0),
            autoCreateTopics = "true", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "itinerary.generation.failed", groupId = CONSUMER)
    @Transactional
    public void onFailed(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventId = event.path("eventId").asText(null);
        if (alreadyProcessed(eventId)) {
            return;
        }

        UUID tripId = UUID.fromString(event.path("tripId").asText());
        // Existing active itinerary (if any) is left completely untouched.
        tripService.setStatus(tripId, TripStatus.DRAFT);

        markProcessed(eventId);
        log.warn("Generation failed for trip {}: {}", tripId, event.path("errorCode").asText("unknown"));
    }

    @DltHandler
    public void onDlt(String message) {
        log.error("Generation event moved to DLT after exhausting retries: {}", message);
    }

    private ItineraryItem itemFrom(UUID dayId, int order, JsonNode itemNode) {
        String title = itemNode.path("title").asText("Untitled");
        ItemType type = parseItemType(itemNode.path("itemType").asText("ACTIVITY"));
        ItineraryItem item = ItineraryItem.create(dayId, order, title, type);
        item.setDescription(itemNode.path("description").asText(null));
        item.setLocationName(itemNode.path("locationName").asText(null));
        if (itemNode.hasNonNull("startTime")) item.setStartTime(LocalTime.parse(itemNode.path("startTime").asText()));
        if (itemNode.hasNonNull("endTime")) item.setEndTime(LocalTime.parse(itemNode.path("endTime").asText()));
        if (itemNode.hasNonNull("estimatedCost")) {
            item.setEstimatedCost(new BigDecimal(itemNode.path("estimatedCost").asText()));
        }
        if (itemNode.hasNonNull("activityId")) {
            item.setCatalogActivityId(UUID.fromString(itemNode.path("activityId").asText()));
            item.setActivitySnapshot(itemNode.toString());
        }
        item.setUserModified(false);
        return item;
    }

    private static ItemType parseItemType(String raw) {
        try {
            return ItemType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ItemType.ACTIVITY;
        }
    }

    private boolean alreadyProcessed(String eventId) {
        if (eventId != null && processedEvents.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return true;
        }
        return false;
    }

    private void markProcessed(String eventId) {
        if (eventId != null) {
            processedEvents.save(new ProcessedEvent(eventId, CONSUMER));
        }
    }
}
