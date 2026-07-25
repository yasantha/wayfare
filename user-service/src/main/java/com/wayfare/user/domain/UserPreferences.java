package com.wayfare.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "travel_style")
    private String travelStyle;

    private String pace;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> interests = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dietary_restrictions", nullable = false, columnDefinition = "jsonb")
    private List<String> dietaryRestrictions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessibility_needs", nullable = false, columnDefinition = "jsonb")
    private List<String> accessibilityNeeds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avoid_tags", nullable = false, columnDefinition = "jsonb")
    private List<String> avoidTags = new ArrayList<>();

    @Column(name = "preferred_accommodation")
    private String preferredAccommodation;

    @Column(nullable = false)
    private int version = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPreferences() {
    }

    public static UserPreferences forUser(UUID userId) {
        UserPreferences p = new UserPreferences();
        p.id = UUID.randomUUID();
        p.userId = userId;
        return p;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Bump the optimistic-ish version each time preferences change (design snapshotting). */
    public void bumpVersion() {
        this.version += 1;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTravelStyle() {
        return travelStyle;
    }

    public void setTravelStyle(String travelStyle) {
        this.travelStyle = travelStyle;
    }

    public String getPace() {
        return pace;
    }

    public void setPace(String pace) {
        this.pace = pace;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests != null ? interests : new ArrayList<>();
    }

    public List<String> getDietaryRestrictions() {
        return dietaryRestrictions;
    }

    public void setDietaryRestrictions(List<String> v) {
        this.dietaryRestrictions = v != null ? v : new ArrayList<>();
    }

    public List<String> getAccessibilityNeeds() {
        return accessibilityNeeds;
    }

    public void setAccessibilityNeeds(List<String> v) {
        this.accessibilityNeeds = v != null ? v : new ArrayList<>();
    }

    public List<String> getAvoidTags() {
        return avoidTags;
    }

    public void setAvoidTags(List<String> v) {
        this.avoidTags = v != null ? v : new ArrayList<>();
    }

    public String getPreferredAccommodation() {
        return preferredAccommodation;
    }

    public void setPreferredAccommodation(String preferredAccommodation) {
        this.preferredAccommodation = preferredAccommodation;
    }

    public int getVersion() {
        return version;
    }
}
