package com.wayfare.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "itinerary_days")
public class ItineraryDay {

    @Id
    private UUID id;

    @Column(name = "itinerary_id", nullable = false)
    private UUID itineraryId;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(nullable = false)
    private LocalDate date;

    private String theme;

    private String notes;

    @Column(name = "estimated_cost")
    private BigDecimal estimatedCost;

    protected ItineraryDay() {
    }

    public static ItineraryDay create(UUID itineraryId, int dayNumber, LocalDate date) {
        ItineraryDay d = new ItineraryDay();
        d.id = UUID.randomUUID();
        d.itineraryId = itineraryId;
        d.dayNumber = dayNumber;
        d.date = date;
        return d;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItineraryId() {
        return itineraryId;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }
}
