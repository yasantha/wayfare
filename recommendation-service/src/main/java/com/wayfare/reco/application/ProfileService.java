package com.wayfare.reco.application;

import com.wayfare.reco.domain.UserInterestProfile;
import com.wayfare.reco.repository.UserInterestProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {

    private final UserInterestProfileRepository profiles;

    public ProfileService(UserInterestProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public UserInterestProfile getOrEmpty(UUID userId) {
        return profiles.findById(userId).orElseGet(() -> UserInterestProfile.forUser(userId));
    }

    @Transactional
    public void applyPreferencesUpdate(UUID userId, List<String> interests, List<String> avoidTags) {
        UserInterestProfile profile = profiles.findById(userId).orElseGet(() -> UserInterestProfile.forUser(userId));
        profile.setInterests(interests);
        profile.setAvoidTags(avoidTags);
        profiles.save(profile);
    }

    @Transactional
    public void applyTripCreated(UUID userId, String destinationId, BigDecimal dailyBudget) {
        UserInterestProfile profile = profiles.findById(userId).orElseGet(() -> UserInterestProfile.forUser(userId));
        profile.addVisitedDestination(destinationId);
        profile.observeDailyBudget(dailyBudget);
        profiles.save(profile);
    }
}
