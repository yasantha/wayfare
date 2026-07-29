package com.wayfare.reco.repository;

import com.wayfare.reco.domain.UserInterestProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserInterestProfileRepository extends JpaRepository<UserInterestProfile, UUID> {
}
