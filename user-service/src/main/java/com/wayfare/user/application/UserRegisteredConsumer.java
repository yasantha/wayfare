package com.wayfare.user.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.user.domain.ProcessedEvent;
import com.wayfare.user.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes {@code user.registered} (produced by Auth's outbox, published in
 * Phase 3) and creates the profile. Idempotent: the event id is recorded in
 * processed_events inside the same transaction, so redelivery is a no-op.
 */
@Component
public class UserRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredConsumer.class);
    private static final String CONSUMER = "user-service";

    private final UserService userService;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public UserRegisteredConsumer(UserService userService, ProcessedEventRepository processedEvents,
                                  ObjectMapper objectMapper) {
        this.userService = userService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "user.registered", groupId = CONSUMER)
    @Transactional
    public void onUserRegistered(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventId = event.path("eventId").asText(null);
        UUID userId = UUID.fromString(event.path("userId").asText());

        if (eventId != null && processedEvents.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }

        userService.ensureProfile(userId);

        if (eventId != null) {
            processedEvents.save(new ProcessedEvent(eventId, CONSUMER));
        }
        log.info("Created profile for registered user {}", userId);
    }
}
