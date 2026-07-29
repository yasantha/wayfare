package com.wayfare.ai.api.dto;

import com.wayfare.ai.domain.GenerationRequest;
import com.wayfare.ai.domain.GenerationStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record GenerationRequestView(
        UUID id,
        UUID tripId,
        GenerationStatus status,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        BigDecimal costUsd,
        String errorCode,
        String errorMessage
) {
    public static GenerationRequestView from(GenerationRequest r) {
        return new GenerationRequestView(r.getId(), r.getTripId(), r.getStatus(), r.getModel(),
                r.getPromptTokens(), r.getCompletionTokens(), r.getCostUsd(), r.getErrorCode(), r.getErrorMessage());
    }
}
