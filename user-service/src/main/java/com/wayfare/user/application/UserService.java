package com.wayfare.user.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.commons.correlation.Correlation;
import com.wayfare.commons.error.Exceptions.ResourceNotFoundException;
import com.wayfare.commons.events.EventEnvelope;
import com.wayfare.user.domain.OutboxEvent;
import com.wayfare.user.domain.UserPreferences;
import com.wayfare.user.domain.UserProfile;
import com.wayfare.user.repository.OutboxRepository;
import com.wayfare.user.repository.UserPreferencesRepository;
import com.wayfare.user.repository.UserProfileRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Profile and preferences. Profiles are created on the {@code user.registered}
 * event, but are also lazily created on first access so the service is usable
 * even before the event arrives (the Kafka backbone lands in Phase 3).
 */
@Service
public class UserService {

    private final UserProfileRepository profiles;
    private final UserPreferencesRepository preferences;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public UserService(UserProfileRepository profiles, UserPreferencesRepository preferences,
                       OutboxRepository outbox, ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.preferences = preferences;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UserProfile getOrCreateProfile(UUID userId) {
        return profiles.findById(userId)
                .orElseGet(() -> profiles.save(UserProfile.forUser(userId)));
    }

    /** Create the profile if it doesn't exist yet; no-op if it does (idempotent). */
    @Transactional
    public void ensureProfile(UUID userId) {
        if (!profiles.existsById(userId)) {
            profiles.save(UserProfile.forUser(userId));
        }
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, Consumer<UserProfile> mutation) {
        UserProfile profile = getOrCreateProfile(userId);
        mutation.accept(profile);
        return profiles.save(profile);
    }

    @Transactional(readOnly = true)
    public UserPreferences getPreferences(UUID userId) {
        return preferences.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No preferences set for user"));
    }

    @Transactional(readOnly = true)
    public UserPreferences getPreferencesOrEmpty(UUID userId) {
        return preferences.findByUserId(userId).orElseGet(() -> UserPreferences.forUser(userId));
    }

    /**
     * Upsert preferences and emit user.preferences.updated via the outbox in the
     * same transaction, so Recommendation and Itinerary AI can refresh downstream.
     */
    @Transactional
    public UserPreferences updatePreferences(UUID userId, Consumer<UserPreferences> mutation) {
        ensureProfile(userId);
        UserPreferences prefs = preferences.findByUserId(userId)
                .orElseGet(() -> UserPreferences.forUser(userId));
        mutation.accept(prefs);
        prefs.bumpVersion();
        UserPreferences saved = preferences.save(prefs);

        outbox.save(OutboxEvent.of("user", userId.toString(), "user.preferences.updated",
                preferencesPayload(saved), MDC.get(Correlation.MDC_KEY)));
        return saved;
    }

    private String preferencesPayload(UserPreferences p) {
        try {
            Map<String, Object> envelope = EventEnvelope.wrap("user.preferences.updated", p.getVersion(),
                    MDC.get(Correlation.MDC_KEY),
                    Map.of("userId", p.getUserId().toString(),
                            "interests", p.getInterests(),
                            "avoidTags", p.getAvoidTags()));
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize preferences payload", e);
        }
    }
}
