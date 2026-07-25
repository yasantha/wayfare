package com.wayfare.user.api.dto;

import com.wayfare.user.domain.UserPreferences;

import java.util.List;

public record PreferencesView(
        String travelStyle,
        String pace,
        List<String> interests,
        List<String> dietaryRestrictions,
        List<String> accessibilityNeeds,
        List<String> avoidTags,
        String preferredAccommodation,
        int version
) {
    public static PreferencesView from(UserPreferences p) {
        return new PreferencesView(p.getTravelStyle(), p.getPace(), p.getInterests(),
                p.getDietaryRestrictions(), p.getAccessibilityNeeds(), p.getAvoidTags(),
                p.getPreferredAccommodation(), p.getVersion());
    }
}
