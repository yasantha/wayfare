package com.wayfare.user.api;

import com.wayfare.user.api.dto.MeView;
import com.wayfare.user.api.dto.PreferencesView;
import com.wayfare.user.api.dto.ProfileView;
import com.wayfare.user.api.dto.UpdatePreferencesRequest;
import com.wayfare.user.api.dto.UpdateProfileRequest;
import com.wayfare.user.application.UserService;
import com.wayfare.user.domain.UserPreferences;
import com.wayfare.user.domain.UserProfile;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Me" endpoints. The user id is taken from the validated JWT subject — never
 * from a request header — so a token for user A can only ever touch user A's
 * data (design §6.3).
 */
@RestController
@RequestMapping("/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeView me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        UserProfile profile = userService.getOrCreateProfile(userId);
        UserPreferences prefs = userService.getPreferencesOrEmpty(userId);
        return new MeView(ProfileView.from(profile), PreferencesView.from(prefs));
    }

    @PatchMapping("/profile")
    public ProfileView updateProfile(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody UpdateProfileRequest req) {
        UserProfile updated = userService.updateProfile(userId(jwt), p -> applyProfile(p, req));
        return ProfileView.from(updated);
    }

    @GetMapping("/preferences")
    public PreferencesView getPreferences(@AuthenticationPrincipal Jwt jwt) {
        return PreferencesView.from(userService.getPreferencesOrEmpty(userId(jwt)));
    }

    @PutMapping("/preferences")
    public PreferencesView putPreferences(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody UpdatePreferencesRequest req) {
        UserPreferences updated = userService.updatePreferences(userId(jwt), p -> applyPreferences(p, req));
        return PreferencesView.from(updated);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static void applyProfile(UserProfile p, UpdateProfileRequest r) {
        if (r.displayName() != null) p.setDisplayName(r.displayName());
        if (r.homeCountry() != null) p.setHomeCountry(r.homeCountry());
        if (r.homeCity() != null) p.setHomeCity(r.homeCity());
        if (r.dateOfBirth() != null) p.setDateOfBirth(r.dateOfBirth());
        if (r.currencyCode() != null) p.setCurrencyCode(r.currencyCode());
        if (r.locale() != null) p.setLocale(r.locale());
        if (r.avatarUrl() != null) p.setAvatarUrl(r.avatarUrl());
    }

    private static void applyPreferences(UserPreferences p, UpdatePreferencesRequest r) {
        p.setTravelStyle(r.travelStyle());
        p.setPace(r.pace());
        p.setInterests(r.interests());
        p.setDietaryRestrictions(r.dietaryRestrictions());
        p.setAccessibilityNeeds(r.accessibilityNeeds());
        p.setAvoidTags(r.avoidTags());
        p.setPreferredAccommodation(r.preferredAccommodation());
    }
}
