package com.wayfare.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generation_requests")
public class GenerationRequest {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenerationStatus status = GenerationStatus.PENDING;

    private String model;

    @Column(name = "prompt_version")
    private Integer promptVersion;

    @Column(name = "prompt_hash", nullable = false)
    private String promptHash;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "cost_usd")
    private BigDecimal costUsd;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected GenerationRequest() {
    }

    public static GenerationRequest create(UUID tripId, UUID userId, String promptHash) {
        GenerationRequest r = new GenerationRequest();
        r.id = UUID.randomUUID();
        r.tripId = tripId;
        r.userId = userId;
        r.promptHash = promptHash;
        return r;
    }

    public void markInProgress() {
        this.status = GenerationStatus.IN_PROGRESS;
        this.attemptCount++;
    }

    public void markSucceeded(String model, int promptTokens, int completionTokens,
                              BigDecimal costUsd, int latencyMs) {
        this.status = GenerationStatus.SUCCEEDED;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.costUsd = costUsd;
        this.latencyMs = latencyMs;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = GenerationStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public UUID getUserId() {
        return userId;
    }

    public GenerationStatus getStatus() {
        return status;
    }

    public String getModel() {
        return model;
    }

    public void setPromptVersion(Integer promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
