package com.wayfare.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    private UUID id;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    private String description;

    private Double latitude;
    private Double longitude;

    @Column(name = "estimated_cost_usd", nullable = false)
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes = 60;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean indoor = false;

    @Column(name = "booking_url")
    private String bookingUrl;

    @Column(nullable = false)
    private boolean active = true;

    protected Activity() {
    }

    public Activity(UUID id, UUID destinationId, String name, String category, String description,
                    Double latitude, Double longitude, BigDecimal estimatedCostUsd,
                    int estimatedDurationMinutes, List<String> tags, BigDecimal rating,
                    boolean indoor, String bookingUrl) {
        this.id = id;
        this.destinationId = destinationId;
        this.name = name;
        this.category = category;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.estimatedCostUsd = estimatedCostUsd;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.tags = tags;
        this.rating = rating;
        this.indoor = indoor;
        this.bookingUrl = bookingUrl;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public int getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public List<String> getTags() {
        return tags;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public boolean isIndoor() {
        return indoor;
    }

    public String getBookingUrl() {
        return bookingUrl;
    }

    public boolean isActive() {
        return active;
    }
}
