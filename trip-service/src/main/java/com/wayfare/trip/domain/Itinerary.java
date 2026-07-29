package com.wayfare.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A version of a trip's plan. New generations are always a NEW row (design
 * §4.4) — a failed or partial regeneration can never damage the version the
 * user already has active.
 */
@Entity
@Table(name = "itineraries")
public class Itinerary {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItinerarySource source = ItinerarySource.MANUAL;

    private String summary;

    @Column(name = "total_estimated_cost")
    private BigDecimal totalEstimatedCost;

    private String currency;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "generation_request_id")
    private UUID generationRequestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Itinerary() {
    }

    public static Itinerary create(UUID tripId, int version, ItinerarySource source, String currency) {
        Itinerary i = new Itinerary();
        i.id = UUID.randomUUID();
        i.tripId = tripId;
        i.version = version;
        i.source = source;
        i.currency = currency;
        return i;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public int getVersion() {
        return version;
    }

    public ItinerarySource getSource() {
        return source;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public BigDecimal getTotalEstimatedCost() {
        return totalEstimatedCost;
    }

    public void setTotalEstimatedCost(BigDecimal totalEstimatedCost) {
        this.totalEstimatedCost = totalEstimatedCost;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getGenerationRequestId() {
        return generationRequestId;
    }

    public void setGenerationRequestId(UUID generationRequestId) {
        this.generationRequestId = generationRequestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
