package com.wayfare.reco.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.reco.domain.ProcessedEvent;
import com.wayfare.reco.repository.ProcessedEventRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Projects {@code trip.created} into the interest profile (visited destinations, budget tier). */
@Component
public class TripCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TripCreatedConsumer.class);
    private static final String CONSUMER = "recommendation-service";

    private final ProfileService profileService;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public TripCreatedConsumer(ProfileService profileService, ProcessedEventRepository processedEvents,
                               ObjectMapper objectMapper) {
        this.profileService = profileService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0),
            autoCreateTopics = "true", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "trip.created", groupId = CONSUMER)
    @Transactional
    public void onTripCreated(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventId = event.path("eventId").asText(null);
        if (eventId != null && processedEvents.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        UUID userId = UUID.fromString(event.path("userId").asText());
        String destinationId = event.path("destinationId").asText(null);
        BigDecimal dailyBudget = dailyBudgetOf(event);

        profileService.applyTripCreated(userId, destinationId, dailyBudget);

        if (eventId != null) {
            processedEvents.save(new ProcessedEvent(eventId, CONSUMER));
        }
        log.info("Projected trip.created for user {} (destination={})", userId, destinationId);
    }

    @DltHandler
    public void onDlt(String message) {
        log.error("trip.created moved to DLT after exhausting retries: {}", message);
    }

    /** budgetAmount is optional on the event — absent unless the trip carried one. */
    private static BigDecimal dailyBudgetOf(JsonNode event) {
        if (!event.hasNonNull("budgetAmount") || !event.hasNonNull("startDate") || !event.hasNonNull("endDate")) {
            return null;
        }
        BigDecimal budgetAmount = new BigDecimal(event.path("budgetAmount").asText());
        LocalDate start = LocalDate.parse(event.path("startDate").asText());
        LocalDate end = LocalDate.parse(event.path("endDate").asText());
        long days = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
        return budgetAmount.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }
}
