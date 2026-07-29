package com.wayfare.trip.api.dto;

import java.time.LocalTime;

public record UpdateItemRequest(
        String title,
        String description,
        LocalTime startTime,
        LocalTime endTime,
        String locationName
) {
}
