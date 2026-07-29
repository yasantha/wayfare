package com.wayfare.ai.repository;

import com.wayfare.ai.domain.GenerationRequest;
import com.wayfare.ai.domain.GenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenerationRequestRepository extends JpaRepository<GenerationRequest, UUID> {

    List<GenerationRequest> findByTripIdOrderByCreatedAtDesc(UUID tripId);

    Optional<GenerationRequest> findFirstByTripIdOrderByCreatedAtDesc(UUID tripId);

    Optional<GenerationRequest> findFirstByPromptHashAndCreatedAtAfterOrderByCreatedAtDesc(
            String promptHash, Instant after);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}
