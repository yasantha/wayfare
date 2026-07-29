package com.wayfare.reco.application;

import com.wayfare.reco.domain.UserInterestProfile;
import com.wayfare.reco.infrastructure.client.CatalogClient.ActivityView;
import com.wayfare.reco.infrastructure.client.CatalogClient.DestinationView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Design §8's formulas, exactly for destinations. For activities, {@code
 * proximityToDayCluster} is folded into rating+cost (see ADR) since it needs
 * day-item geodata this service doesn't own without a sync call design §3.1
 * doesn't list; {@code closedOnDate} is dropped since Catalog has no opening-
 * hours data. Both simplifications are documented, not silent.
 */
@Service
public class RuleBasedRecommendationEngine implements RecommendationEngine {

    private final WeightsService weightsService;

    public RuleBasedRecommendationEngine(WeightsService weightsService) {
        this.weightsService = weightsService;
    }

    @Override
    public List<ScoredDestination> scoreDestinations(UserInterestProfile profile, List<DestinationView> candidates,
                                                      int travelMonth) {
        Map<String, BigDecimal> w = weightsService.currentWeights();
        Set<String> interests = new HashSet<>(profile.getInterests());

        return candidates.stream()
                .map(d -> {
                    double interestScore = tagOverlap(interests, d.tags());
                    double budgetScore = budgetFit(profile.getAvgBudgetTier(), d.avgDailyCostUsd());
                    double seasonScore = seasonFit(travelMonth, d.bestMonths());
                    double popularityScore = normalizedPopularity(d.popularityScore());

                    double score = weight(w, "destination.interestMatch", 0.35) * interestScore
                            + weight(w, "destination.budgetFit", 0.25) * budgetScore
                            + weight(w, "destination.seasonFit", 0.20) * seasonScore
                            + weight(w, "destination.popularity", 0.20) * popularityScore;

                    return new ScoredDestination(d, round(score));
                })
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();
    }

    @Override
    public List<ScoredActivity> scoreActivities(UserInterestProfile profile, List<ActivityView> candidates,
                                                BigDecimal remainingBudget, Set<String> excludeActivityIds) {
        Map<String, BigDecimal> w = weightsService.currentWeights();
        Set<String> interests = new HashSet<>(profile.getInterests());
        Set<String> avoidTags = new HashSet<>(profile.getAvoidTags());

        return candidates.stream()
                .filter(a -> !excludeActivityIds.contains(a.id().toString())) // already in the itinerary
                .map(a -> {
                    double tagScore = tagOverlap(interests, a.tags());
                    double ratingScore = ratingNormalized(a.rating());
                    double costScore = costFit(remainingBudget, a.estimatedCostUsd());

                    double score = weight(w, "activity.tagOverlap", 0.40) * tagScore
                            + weight(w, "activity.rating", 0.30) * ratingScore
                            + weight(w, "activity.costFit", 0.30) * costScore;

                    boolean avoided = a.tags() != null && a.tags().stream().anyMatch(avoidTags::contains);
                    if (avoided) {
                        score *= 0.1; // penalty, not exclusion — design §8 says "penalty"
                    }

                    return new ScoredActivity(a, round(score));
                })
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();
    }

    private static double tagOverlap(Set<String> interests, List<String> tags) {
        if (interests.isEmpty() || tags == null || tags.isEmpty()) {
            return 0.0;
        }
        long matches = tags.stream().filter(interests::contains).count();
        return Math.min(1.0, (double) matches / interests.size());
    }

    private static double budgetFit(BigDecimal userTier, BigDecimal destinationCost) {
        if (userTier == null || destinationCost == null) {
            return 0.5; // neutral — not enough data to judge
        }
        double a = userTier.doubleValue();
        double b = destinationCost.doubleValue();
        double diff = Math.abs(a - b);
        double denom = Math.max(Math.max(a, b), 1.0);
        return Math.max(0.0, 1.0 - diff / denom);
    }

    private static double seasonFit(int travelMonth, List<Integer> bestMonths) {
        if (bestMonths == null || bestMonths.isEmpty()) {
            return 0.5;
        }
        return bestMonths.contains(travelMonth) ? 1.0 : 0.2;
    }

    private static double normalizedPopularity(BigDecimal popularityScore) {
        if (popularityScore == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, popularityScore.doubleValue() / 100.0));
    }

    private static double ratingNormalized(BigDecimal rating) {
        if (rating == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, rating.doubleValue() / 5.0));
    }

    private static double costFit(BigDecimal remainingBudget, BigDecimal cost) {
        if (remainingBudget == null || cost == null) {
            return 0.5;
        }
        if (cost.compareTo(remainingBudget) > 0) {
            return 0.0; // would exceed what's left
        }
        double ratio = remainingBudget.doubleValue() <= 0
                ? 1.0
                : cost.doubleValue() / remainingBudget.doubleValue();
        return 1.0 - ratio * 0.5; // cheaper (relative to remaining budget) scores slightly higher
    }

    private static double weight(Map<String, BigDecimal> weights, String key, double fallback) {
        BigDecimal w = weights.get(key);
        return w != null ? w.doubleValue() : fallback;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
