package com.wayfare.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "destination_id")
    private UUID destinationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "destination_snapshot", columnDefinition = "jsonb")
    private String destinationSnapshot;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "traveler_count", nullable = false)
    private int travelerCount = 1;

    @Column(name = "budget_amount")
    private BigDecimal budgetAmount;

    @Column(name = "budget_currency")
    private String budgetCurrency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences_snapshot", columnDefinition = "jsonb")
    private String preferencesSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status = TripStatus.DRAFT;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Trip() {
    }

    public static Trip create(UUID userId, String title, UUID destinationId, String destinationSnapshot,
                              LocalDate startDate, LocalDate endDate, int travelerCount,
                              BigDecimal budgetAmount, String budgetCurrency) {
        Trip t = new Trip();
        t.id = UUID.randomUUID();
        t.userId = userId;
        t.title = title;
        t.destinationId = destinationId;
        t.destinationSnapshot = destinationSnapshot;
        t.startDate = startDate;
        t.endDate = endDate;
        t.travelerCount = travelerCount;
        t.budgetAmount = budgetAmount;
        t.budgetCurrency = budgetCurrency;
        return t;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isOwnedBy(UUID candidateUserId) {
        return userId.equals(candidateUserId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public String getDestinationSnapshot() {
        return destinationSnapshot;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getTravelerCount() {
        return travelerCount;
    }

    public void setTravelerCount(int travelerCount) {
        this.travelerCount = travelerCount;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public String getBudgetCurrency() {
        return budgetCurrency;
    }

    public void setBudgetCurrency(String budgetCurrency) {
        this.budgetCurrency = budgetCurrency;
    }

    public String getPreferencesSnapshot() {
        return preferencesSnapshot;
    }

    public void setPreferencesSnapshot(String preferencesSnapshot) {
        this.preferencesSnapshot = preferencesSnapshot;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
