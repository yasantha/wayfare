package com.wayfare.reco.application;

import com.wayfare.reco.domain.UserInterestProfile;
import com.wayfare.reco.infrastructure.client.CatalogClient.ActivityView;
import com.wayfare.reco.infrastructure.client.CatalogClient.DestinationView;

import java.util.List;
import java.util.Set;

/**
 * Scoring port (design §8): rule-based today, shaped so a learned model can
 * replace it behind the same contract without touching callers.
 */
public interface RecommendationEngine {

    List<ScoredDestination> scoreDestinations(UserInterestProfile profile, List<DestinationView> candidates,
                                              int travelMonth);

    List<ScoredActivity> scoreActivities(UserInterestProfile profile, List<ActivityView> candidates,
                                         java.math.BigDecimal remainingBudget, Set<String> excludeActivityIds);

    record ScoredDestination(DestinationView destination, double score) {
    }

    record ScoredActivity(ActivityView activity, double score) {
    }
}
