package com.wayfare.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "itinerary_items")
public class ItineraryItem {

    @Id
    private UUID id;

    @Column(name = "itinerary_day_id", nullable = false)
    private UUID itineraryDayId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "catalog_activity_id")
    private UUID catalogActivityId;

    /** Denormalised copy of the catalog activity at add-time (design §4.3). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "activity_snapshot", columnDefinition = "jsonb")
    private String activitySnapshot;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "location_name")
    private String locationName;

    private Double latitude;
    private Double longitude;

    @Column(name = "estimated_cost")
    private BigDecimal estimatedCost;

    @Column(name = "booking_url")
    private String bookingUrl;

    @Column(name = "user_modified", nullable = false)
    private boolean userModified = false;

    protected ItineraryItem() {
    }

    public static ItineraryItem create(UUID itineraryDayId, int sortOrder, String title, ItemType itemType) {
        ItineraryItem item = new ItineraryItem();
        item.id = UUID.randomUUID();
        item.itineraryDayId = itineraryDayId;
        item.sortOrder = sortOrder;
        item.title = title;
        item.itemType = itemType;
        return item;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItineraryDayId() {
        return itineraryDayId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public UUID getCatalogActivityId() {
        return catalogActivityId;
    }

    public void setCatalogActivityId(UUID catalogActivityId) {
        this.catalogActivityId = catalogActivityId;
    }

    public String getActivitySnapshot() {
        return activitySnapshot;
    }

    public void setActivitySnapshot(String activitySnapshot) {
        this.activitySnapshot = activitySnapshot;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public String getBookingUrl() {
        return bookingUrl;
    }

    public void setBookingUrl(String bookingUrl) {
        this.bookingUrl = bookingUrl;
    }

    public boolean isUserModified() {
        return userModified;
    }

    public void setUserModified(boolean userModified) {
        this.userModified = userModified;
    }
}
