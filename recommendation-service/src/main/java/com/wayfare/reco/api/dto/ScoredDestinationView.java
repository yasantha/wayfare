package com.wayfare.reco.api.dto;

import com.wayfare.reco.application.RecommendationEngine.ScoredDestination;

import java.util.UUID;

public record ScoredDestinationView(UUID id, String name, String countryCode, double score) {
    public static ScoredDestinationView from(ScoredDestination s) {
        return new ScoredDestinationView(s.destination().id(), s.destination().name(),
                s.destination().countryCode(), s.score());
    }
}
