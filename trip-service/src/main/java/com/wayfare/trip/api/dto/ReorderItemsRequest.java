package com.wayfare.trip.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderItemsRequest(
        @NotEmpty List<UUID> itemIds
) {
}
