package com.wayfare.reco.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Tunable without redeployment (design §8). */
@Entity
@Table(name = "scoring_weights")
public class ScoringWeight {

    @Id
    private String key;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ScoringWeight() {
    }

    public String getKey() {
        return key;
    }

    public BigDecimal getValue() {
        return value;
    }
}
