package com.wayfare.trip.api.dto;

import com.wayfare.trip.domain.ItemType;
import com.wayfare.trip.domain.ItineraryItem;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public record ItineraryItemView(
        UUID id,
        int sortOrder,
        UUID catalogActivityId,
        String title,
        String description,
        ItemType itemType,
        LocalTime startTime,
        LocalTime endTime,
        String locationName,
        Double latitude,
        Double longitude,
        BigDecimal estimatedCost,
        String bookingUrl,
        boolean userModified
) {
    public static ItineraryItemView from(ItineraryItem i) {
        return new ItineraryItemView(i.getId(), i.getSortOrder(), i.getCatalogActivityId(), i.getTitle(),
                i.getDescription(), i.getItemType(), i.getStartTime(), i.getEndTime(), i.getLocationName(),
                i.getLatitude(), i.getLongitude(), i.getEstimatedCost(), i.getBookingUrl(), i.isUserModified());
    }
}
