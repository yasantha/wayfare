package com.wayfare.reco.application;

import com.wayfare.reco.domain.ScoringWeight;
import com.wayfare.reco.domain.UserInterestProfile;
import com.wayfare.reco.infrastructure.client.CatalogClient.ActivityView;
import com.wayfare.reco.infrastructure.client.CatalogClient.DestinationView;
import com.wayfare.reco.repository.ScoringWeightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleBasedRecommendationEngineTest {

    @Mock
    ScoringWeightRepository weightsRepo;

    private RuleBasedRecommendationEngine engine() {
        when(weightsRepo.findAll()).thenReturn(List.of());
        return new RuleBasedRecommendationEngine(new WeightsService(weightsRepo));
    }

    private UserInterestProfile profileWith(List<String> interests, List<String> avoidTags, BigDecimal budgetTier) {
        UserInterestProfile p = UserInterestProfile.forUser(UUID.randomUUID());
        p.setInterests(interests);
        p.setAvoidTags(avoidTags);
        p.observeDailyBudget(budgetTier);
        return p;
    }

    @Test
    void destinationMatchingInterests_scoresHigherThanNonMatching() {
        UserInterestProfile profile = profileWith(List.of("culture", "food"), List.of(), null);
        DestinationView matching = new DestinationView(UUID.randomUUID(), "Kyoto", "JP",
                List.of(3, 4), BigDecimal.valueOf(100), BigDecimal.valueOf(90),
                List.of("culture", "food", "history"));
        DestinationView nonMatching = new DestinationView(UUID.randomUUID(), "Somewhere", "XX",
                List.of(3, 4), BigDecimal.valueOf(100), BigDecimal.valueOf(90),
                List.of("beach", "nightlife"));

        var results = engine().scoreDestinations(profile, List.of(nonMatching, matching), 3);

        assertThat(results.get(0).destination().name()).isEqualTo("Kyoto");
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    }

    @Test
    void destinationInSeason_scoresHigherThanOffSeason() {
        UserInterestProfile profile = profileWith(List.of(), List.of(), null);
        DestinationView inSeason = new DestinationView(UUID.randomUUID(), "A", "AA",
                List.of(6), BigDecimal.valueOf(100), BigDecimal.valueOf(50), List.of());
        DestinationView offSeason = new DestinationView(UUID.randomUUID(), "B", "BB",
                List.of(12), BigDecimal.valueOf(100), BigDecimal.valueOf(50), List.of());

        var results = engine().scoreDestinations(profile, List.of(offSeason, inSeason), 6);

        assertThat(results.get(0).destination().name()).isEqualTo("A");
    }

    @Test
    void activity_alreadyInItinerary_isExcludedEntirely() {
        UserInterestProfile profile = profileWith(List.of("culture"), List.of(), null);
        UUID excludedId = UUID.randomUUID();
        ActivityView excluded = new ActivityView(excludedId, UUID.randomUUID(), "Shrine",
                BigDecimal.TEN, List.of("culture"), BigDecimal.valueOf(4.5));
        ActivityView included = new ActivityView(UUID.randomUUID(), UUID.randomUUID(), "Museum",
                BigDecimal.TEN, List.of("culture"), BigDecimal.valueOf(4.0));

        var results = engine().scoreActivities(profile, List.of(excluded, included), null,
                Set.of(excludedId.toString()));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).activity().name()).isEqualTo("Museum");
    }

    @Test
    void activity_withAvoidedTag_isPenalizedNotExcluded() {
        UserInterestProfile profile = profileWith(List.of("nightlife"), List.of("crowded"), null);
        ActivityView avoided = new ActivityView(UUID.randomUUID(), UUID.randomUUID(), "Busy Club",
                BigDecimal.TEN, List.of("nightlife", "crowded"), BigDecimal.valueOf(4.5));
        ActivityView clean = new ActivityView(UUID.randomUUID(), UUID.randomUUID(), "Quiet Bar",
                BigDecimal.TEN, List.of("nightlife"), BigDecimal.valueOf(4.5));

        var results = engine().scoreActivities(profile, List.of(avoided, clean), null, Set.of());

        assertThat(results).hasSize(2);
        double avoidedScore = results.stream().filter(r -> r.activity().name().equals("Busy Club"))
                .findFirst().orElseThrow().score();
        double cleanScore = results.stream().filter(r -> r.activity().name().equals("Quiet Bar"))
                .findFirst().orElseThrow().score();
        assertThat(avoidedScore).isLessThan(cleanScore);
    }

    @Test
    void activity_overRemainingBudget_scoresZeroOnCostFit() {
        UserInterestProfile profile = profileWith(List.of(), List.of(), null);
        ActivityView expensive = new ActivityView(UUID.randomUUID(), UUID.randomUUID(), "Expensive",
                BigDecimal.valueOf(500), List.of(), BigDecimal.ZERO);

        var results = engine().scoreActivities(profile, List.of(expensive), BigDecimal.valueOf(50), Set.of());

        // tagOverlap=0, rating=0, costFit=0 (over budget) -> total score 0
        assertThat(results.get(0).score()).isZero();
    }

    @Test
    void configuredWeights_areUsedInsteadOfDefaults() {
        ScoringWeight allPopularity = mockWeight("destination.popularity", "1.0000");
        ScoringWeight zeroInterest = mockWeight("destination.interestMatch", "0.0000");
        ScoringWeight zeroBudget = mockWeight("destination.budgetFit", "0.0000");
        ScoringWeight zeroSeason = mockWeight("destination.seasonFit", "0.0000");
        when(weightsRepo.findAll()).thenReturn(
                List.of(allPopularity, zeroInterest, zeroBudget, zeroSeason));

        RuleBasedRecommendationEngine engine = new RuleBasedRecommendationEngine(new WeightsService(weightsRepo));
        UserInterestProfile profile = profileWith(List.of("culture"), List.of(), null);
        DestinationView highPopularity = new DestinationView(UUID.randomUUID(), "Popular", "PP",
                List.of(), BigDecimal.ZERO, BigDecimal.valueOf(100), List.of());

        var results = engine.scoreDestinations(profile, List.of(highPopularity), 1);

        assertThat(results.get(0).score()).isEqualTo(1.0);
    }

    private static ScoringWeight mockWeight(String key, String value) {
        ScoringWeight w = org.mockito.Mockito.mock(ScoringWeight.class);
        when(w.getKey()).thenReturn(key);
        when(w.getValue()).thenReturn(new BigDecimal(value));
        return w;
    }
}
