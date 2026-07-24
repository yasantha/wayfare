package com.wayfare.auth.spike;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** THROWAWAY Phase 0 spike entity. Delete with the rest of this package. */
@Entity
@Table(name = "spike_ping")
public class SpikePing {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SpikePing() {
    }

    public SpikePing(String note) {
        this.id = UUID.randomUUID();
        this.note = note;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
