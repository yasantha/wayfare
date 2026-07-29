package com.wayfare.trip.repository;

import com.wayfare.trip.domain.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    List<ItineraryItem> findByItineraryDayIdOrderBySortOrder(UUID itineraryDayId);
}
