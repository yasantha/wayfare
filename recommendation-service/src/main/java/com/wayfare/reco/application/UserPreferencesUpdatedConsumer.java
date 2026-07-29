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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Projects {@code user.preferences.updated} into the interest profile (design §4.3). */
@Component
public class UserPreferencesUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserPreferencesUpdatedConsumer.class);
    private static final String CONSUMER = "recommendation-service";

    private final ProfileService profileService;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public UserPreferencesUpdatedConsumer(ProfileService profileService, ProcessedEventRepository processedEvents,
                                          ObjectMapper objectMapper) {
        this.profileService = profileService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0),
            autoCreateTopics = "true", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "user.preferences.updated", groupId = CONSUMER)
    @Transactional
    public void onPreferencesUpdated(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventId = event.path("eventId").asText(null);
        if (eventId != null && processedEvents.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        UUID userId = UUID.fromString(event.path("userId").asText());
        List<String> interests = toList(event.path("interests"));
        List<String> avoidTags = toList(event.path("avoidTags"));

        profileService.applyPreferencesUpdate(userId, interests, avoidTags);

        if (eventId != null) {
            processedEvents.save(new ProcessedEvent(eventId, CONSUMER));
        }
        log.info("Projected preferences for user {}", userId);
    }

    @DltHandler
    public void onDlt(String message) {
        log.error("user.preferences.updated moved to DLT after exhausting retries: {}", message);
    }

    private static List<String> toList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(n -> values.add(n.asText()));
        return values;
    }
}
