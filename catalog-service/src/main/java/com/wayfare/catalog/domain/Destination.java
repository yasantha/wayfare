package com.wayfare.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "destinations")
public class Destination {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    private String region;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private String timezone;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "best_months", nullable = false, columnDefinition = "jsonb")
    private List<Integer> bestMonths = new ArrayList<>();

    @Column(name = "avg_daily_cost_usd", nullable = false)
    private BigDecimal avgDailyCostUsd;

    @Column(name = "popularity_score", nullable = false)
    private BigDecimal popularityScore = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Destination() {
    }

    public Destination(UUID id, String name, String countryCode, String region,
                       double latitude, double longitude, String timezone, String description,
                       List<Integer> bestMonths, BigDecimal avgDailyCostUsd,
                       BigDecimal popularityScore, List<String> tags) {
        this.id = id;
        this.name = name;
        this.countryCode = countryCode;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timezone = timezone;
        this.description = description;
        this.bestMonths = bestMonths;
        this.avgDailyCostUsd = avgDailyCostUsd;
        this.popularityScore = popularityScore;
        this.tags = tags;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegion() {
        return region;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getDescription() {
        return description;
    }

    public List<Integer> getBestMonths() {
        return bestMonths;
    }

    public BigDecimal getAvgDailyCostUsd() {
        return avgDailyCostUsd;
    }

    public BigDecimal getPopularityScore() {
        return popularityScore;
    }

    public List<String> getTags() {
        return tags;
    }
}
