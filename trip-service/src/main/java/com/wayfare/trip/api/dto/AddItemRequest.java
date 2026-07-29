package com.wayfare.trip.api.dto;

import com.wayfare.trip.domain.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record AddItemRequest(
        @NotBlank String title,
        @NotNull ItemType itemType,
        String description,
        LocalTime startTime,
        LocalTime endTime
) {
}
