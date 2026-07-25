package com.wayfare.user.api.dto;

import java.util.List;

/** Replace semantics: the provided lists overwrite the stored ones. */
public record UpdatePreferencesRequest(
        String travelStyle,
        String pace,
        List<String> interests,
        List<String> dietaryRestrictions,
        List<String> accessibilityNeeds,
        List<String> avoidTags,
        String preferredAccommodation
) {
}
