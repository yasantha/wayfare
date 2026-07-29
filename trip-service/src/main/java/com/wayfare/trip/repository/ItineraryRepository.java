package com.wayfare.trip.repository;

import com.wayfare.trip.domain.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    List<Itinerary> findByTripIdOrderByVersionDesc(UUID tripId);

    @Query("select coalesce(max(i.version), 0) from Itinerary i where i.tripId = :tripId")
    int findMaxVersion(@Param("tripId") UUID tripId);

    /** Deactivate every version for a trip before activating a new one (single active version invariant). */
    @Modifying
    @Query("update Itinerary i set i.active = false where i.tripId = :tripId and i.active = true")
    int deactivateAllForTrip(@Param("tripId") UUID tripId);
}
