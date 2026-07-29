package com.wayfare.reco.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-model projection (design §4.3), built entirely from consumed events —
 * {@code user.preferences.updated} and {@code trip.created}. Never populated
 * via a sync call to User Service.
 */
@Entity
@Table(name = "user_interest_profiles")
public class UserInterestProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> interests = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avoid_tags", nullable = false, columnDefinition = "jsonb")
    private List<String> avoidTags = new ArrayList<>();

    @Column(name = "avg_budget_tier")
    private BigDecimal avgBudgetTier;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visited_destination_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> visitedDestinationIds = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserInterestProfile() {
    }

    public static UserInterestProfile forUser(UUID userId) {
        UserInterestProfile p = new UserInterestProfile();
        p.userId = userId;
        return p;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Running average: folds a new per-day budget observation into the existing tier. */
    public void observeDailyBudget(BigDecimal dailyAmount) {
        if (dailyAmount == null) {
            return;
        }
        this.avgBudgetTier = avgBudgetTier == null
                ? dailyAmount
                : avgBudgetTier.add(dailyAmount).divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
    }

    public void addVisitedDestination(String destinationId) {
        if (destinationId != null && !destinationId.isBlank() && !visitedDestinationIds.contains(destinationId)) {
            visitedDestinationIds.add(destinationId);
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests != null ? interests : new ArrayList<>();
    }

    public List<String> getAvoidTags() {
        return avoidTags;
    }

    public void setAvoidTags(List<String> avoidTags) {
        this.avoidTags = avoidTags != null ? avoidTags : new ArrayList<>();
    }

    public BigDecimal getAvgBudgetTier() {
        return avgBudgetTier;
    }

    public List<String> getVisitedDestinationIds() {
        return visitedDestinationIds;
    }
}
