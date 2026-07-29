package com.wayfare.reco.api.dto;

import com.wayfare.reco.application.RecommendationEngine.ScoredActivity;

import java.math.BigDecimal;
import java.util.UUID;

public record ScoredActivityView(UUID id, String name, BigDecimal estimatedCostUsd, double score) {
    public static ScoredActivityView from(ScoredActivity s) {
        return new ScoredActivityView(s.activity().id(), s.activity().name(),
                s.activity().estimatedCostUsd(), s.score());
    }
}
