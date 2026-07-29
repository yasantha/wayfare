package com.wayfare.reco.api;

import com.wayfare.reco.api.dto.ScoredActivityView;
import com.wayfare.reco.api.dto.ScoredDestinationView;
import com.wayfare.reco.application.ProfileService;
import com.wayfare.reco.application.RecommendationEngine;
import com.wayfare.reco.domain.UserInterestProfile;
import com.wayfare.reco.infrastructure.client.CatalogClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * design §5.2. Scoring never calls User Service on the request path — the
 * interest profile is a local projection (design §4.3) — and never calls
 * Trip Service either: trip context (destination, remaining budget, items
 * already added) travels on the request the same way generation parameters
 * do for Itinerary AI (design ADR-011's pattern), since the client already
 * has that data from Trip Service.
 */
@RestController
public class RecommendationController {

    private final ProfileService profileService;
    private final CatalogClient catalogClient;
    private final RecommendationEngine engine;

    public RecommendationController(ProfileService profileService, CatalogClient catalogClient,
                                    RecommendationEngine engine) {
        this.profileService = profileService;
        this.catalogClient = catalogClient;
        this.engine = engine;
    }

    @GetMapping("/recommendations/destinations")
    public List<ScoredDestinationView> destinations(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) Integer travelMonth,
                                                     @RequestParam(defaultValue = "10") int limit) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserInterestProfile profile = profileService.getOrEmpty(userId);
        int month = travelMonth != null ? travelMonth : LocalDate.now().getMonthValue();

        List<CatalogClient.DestinationView> candidates = catalogClient.fetchDestinations(100);
        return engine.scoreDestinations(profile, candidates, month).stream()
                .limit(limit)
                .map(ScoredDestinationView::from)
                .toList();
    }

    @GetMapping("/trips/{tripId}/recommendations/activities")
    public List<ScoredActivityView> activities(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID tripId,
                                               @RequestParam UUID destinationId,
                                               @RequestParam(required = false) BigDecimal remainingBudget,
                                               @RequestParam(required = false) List<UUID> excludeActivityIds,
                                               @RequestParam(defaultValue = "10") int limit) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserInterestProfile profile = profileService.getOrEmpty(userId);
        Set<String> excluded = excludeActivityIds == null ? Set.of()
                : excludeActivityIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet());

        List<CatalogClient.ActivityView> candidates = catalogClient.fetchActivitiesForDestination(destinationId, 100);
        return engine.scoreActivities(profile, candidates, remainingBudget, excluded).stream()
                .limit(limit)
                .map(ScoredActivityView::from)
                .toList();
    }
}
