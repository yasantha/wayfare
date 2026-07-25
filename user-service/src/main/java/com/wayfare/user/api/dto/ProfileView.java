package com.wayfare.user.api.dto;

import com.wayfare.user.domain.UserProfile;

import java.time.LocalDate;
import java.util.UUID;

public record ProfileView(
        UUID userId,
        String displayName,
        String homeCountry,
        String homeCity,
        LocalDate dateOfBirth,
        String currencyCode,
        String locale,
        String avatarUrl
) {
    public static ProfileView from(UserProfile p) {
        return new ProfileView(p.getUserId(), p.getDisplayName(), p.getHomeCountry(),
                p.getHomeCity(), p.getDateOfBirth(), p.getCurrencyCode(), p.getLocale(), p.getAvatarUrl());
    }
}
