package com.wayfare.ai.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.ai.domain.GenerationPayload;
import com.wayfare.ai.domain.GenerationRequest;
import com.wayfare.ai.repository.GenerationPayloadRepository;
import com.wayfare.ai.repository.GenerationRequestRepository;
import com.wayfare.ai.repository.OutboxRepository;
import com.wayfare.ai.domain.OutboxEvent;
import com.wayfare.commons.events.EventEnvelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Atomic persistence of generation-request state: creation, status, payload,
 * and the matching outbox row all commit together (design §3.3 transactional
 * outbox) — a separate bean from {@code GenerationService}/{@code
 * GenerationWorker} so {@code @Transactional} isn't bypassed by same-class
 * self-invocation (Spring's proxy-based AOP never applies to a method called
 * via {@code this.method()} from within its own class).
 */
@Service
public class GenerationOutcomeService {

    private static final Duration DEDUPE_WINDOW = Duration.ofMinutes(10);

    /** Placeholder blended rate; real per-model vendor pricing is out of scope for this MVP. */
    private static final BigDecimal COST_PER_1K_TOKENS_USD = BigDecimal.valueOf(0.002);

    private final GenerationRequestRepository requests;
    private final GenerationPayloadRepository payloads;
    private final OutboxRepository outbox;
    private final QuotaService quotaService;
    private final ObjectMapper objectMapper;

    public GenerationOutcomeService(GenerationRequestRepository requests, GenerationPayloadRepository payloads,
                                    OutboxRepository outbox, QuotaService quotaService, ObjectMapper objectMapper) {
        this.requests = requests;
        this.payloads = payloads;
        this.outbox = outbox;
        this.quotaService = quotaService;
        this.objectMapper = objectMapper;
    }

    /**
     * Dedupes by prompt hash within a window, checks quota, and persists a
     * PENDING row — all in one transaction so the row is guaranteed committed
     * by the time this method returns to its (non-transactional) caller, which
     * only then dispatches the async worker.
     */
    @Transactional
    public PendingResult createPending(UUID tripId, UUID userId, String promptHash) {
        var existing = requests.findFirstByPromptHashAndCreatedAtAfterOrderByCreatedAtDesc(
                promptHash, Instant.now().minus(DEDUPE_WINDOW));
        if (existing.isPresent()) {
            return new PendingResult(existing.get(), false);
        }

        quotaService.checkQuota(userId);

        GenerationRequest request = GenerationRequest.create(tripId, userId, promptHash);
        requests.save(request);
        return new PendingResult(request, true);
    }

    public record PendingResult(GenerationRequest request, boolean isNew) {
    }

    @Transactional
    public void markInProgress(UUID requestId) {
        GenerationRequest request = requests.findById(requestId).orElseThrow();
        request.markInProgress();
        requests.save(request);
    }

    @Transactional
    public void recordSuccess(UUID requestId, String model, int promptTokens, int completionTokens,
                              int latencyMs, String rawJson, String validatedJson, String correlationId) {
        GenerationRequest request = requests.findById(requestId).orElseThrow();
        BigDecimal cost = BigDecimal.valueOf(promptTokens + completionTokens)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(COST_PER_1K_TOKENS_USD)
                .setScale(4, RoundingMode.HALF_UP);
        request.markSucceeded(model, promptTokens, completionTokens, cost, latencyMs);
        requests.save(request);
        payloads.save(GenerationPayload.of(requestId, rawJson, validatedJson));

        outbox.save(OutboxEvent.of("generation_request", requestId.toString(),
                "itinerary.generation.succeeded", succeededPayload(request, validatedJson, correlationId), correlationId));
    }

    @Transactional
    public void recordFailure(UUID requestId, String errorCode, String errorMessage, String correlationId) {
        GenerationRequest request = requests.findById(requestId).orElseThrow();
        request.markFailed(errorCode, errorMessage);
        requests.save(request);

        outbox.save(OutboxEvent.of("generation_request", requestId.toString(),
                "itinerary.generation.failed", failedPayload(request, errorCode, correlationId), correlationId));
    }

    /**
     * Wraps the validated itinerary JSON (already shaped exactly as design §7.4
     * specifies) in the shared envelope, adding requestId/tripId so Trip
     * Service's consumer (built ahead of this in Phase 4) can persist it.
     */
    private String succeededPayload(GenerationRequest request, String validatedJson, String correlationId) {
        try {
            Map<String, Object> itineraryFields = objectMapper.readValue(validatedJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> data = new java.util.LinkedHashMap<>(itineraryFields);
            data.put("requestId", request.getId().toString());
            data.put("tripId", request.getTripId().toString());
            Map<String, Object> envelope = EventEnvelope.wrap("itinerary.generation.succeeded", 1,
                    correlationId, data);
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize generation.succeeded payload", e);
        }
    }

    private String failedPayload(GenerationRequest request, String errorCode, String correlationId) {
        try {
            Map<String, Object> envelope = EventEnvelope.wrap("itinerary.generation.failed", 1, correlationId,
                    Map.of("requestId", request.getId().toString(), "tripId", request.getTripId().toString(),
                            "errorCode", errorCode, "retryable", true));
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize generation.failed payload", e);
        }
    }
}
