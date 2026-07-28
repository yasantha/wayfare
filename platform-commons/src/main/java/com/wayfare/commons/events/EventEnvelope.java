package com.wayfare.commons.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standard envelope for every outbox/Kafka event (design §3.2): {@code eventId},
 * {@code eventType}, {@code version}, {@code occurredAt}, {@code correlationId},
 * with the event's own fields flattened alongside. Consumers read {@code eventId}
 * for the {@code processed_events} idempotency check.
 */
public final class EventEnvelope {

    private EventEnvelope() {
    }

    public static Map<String, Object> wrap(String eventType, int version, String correlationId,
                                           Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("version", version);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("correlationId", correlationId);
        envelope.putAll(data);
        return envelope;
    }
}
