package com.wayfare.user.api.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** All fields optional; only non-null values are applied (partial update). */
public record UpdateProfileRequest(
        @Size(max = 120) String displayName,
        @Size(min = 2, max = 2) String homeCountry,
        @Size(max = 120) String homeCity,
        LocalDate dateOfBirth,
        @Size(min = 3, max = 3) String currencyCode,
        @Size(max = 16) String locale,
        @Size(max = 512) String avatarUrl
) {
}
