package com.wayfare.trip.repository;

import com.wayfare.trip.domain.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {

    List<ItineraryDay> findByItineraryIdOrderByDayNumber(UUID itineraryId);
}
