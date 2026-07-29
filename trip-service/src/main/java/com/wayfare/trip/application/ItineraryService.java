package com.wayfare.trip.application;

import com.wayfare.commons.error.Exceptions.ResourceNotFoundException;
import com.wayfare.commons.error.Exceptions.ValidationException;
import com.wayfare.trip.domain.Itinerary;
import com.wayfare.trip.domain.ItineraryDay;
import com.wayfare.trip.domain.ItineraryItem;
import com.wayfare.trip.domain.ItinerarySource;
import com.wayfare.trip.domain.ItemType;
import com.wayfare.trip.domain.Trip;
import com.wayfare.trip.domain.TripStatus;
import com.wayfare.trip.repository.ItineraryDayRepository;
import com.wayfare.trip.repository.ItineraryItemRepository;
import com.wayfare.trip.repository.ItineraryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Itinerary versioning + days/items editing. Ownership is always resolved
 * through the parent trip (design §6.3) — never trust an itinerary/day/item id
 * alone, always walk back to the trip and check {@code userId}.
 */
@Service
public class ItineraryService {

    private final TripService tripService;
    private final ItineraryRepository itineraries;
    private final ItineraryDayRepository days;
    private final ItineraryItemRepository items;

    public ItineraryService(TripService tripService, ItineraryRepository itineraries,
                            ItineraryDayRepository days, ItineraryItemRepository items) {
        this.tripService = tripService;
        this.itineraries = itineraries;
        this.days = days;
        this.items = items;
    }

    /** New manual version, pre-seeded with one empty day per day of the trip's date range. */
    @Transactional
    public Itinerary createManualVersion(UUID tripId, UUID userId) {
        Trip trip = tripService.getForItineraryAccess(tripId, userId);

        int nextVersion = itineraries.findMaxVersion(tripId) + 1;
        Itinerary itinerary = Itinerary.create(tripId, nextVersion, ItinerarySource.MANUAL, trip.getBudgetCurrency());
        itineraries.save(itinerary);

        long dayCount = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        for (int i = 0; i < dayCount; i++) {
            days.save(ItineraryDay.create(itinerary.getId(), i + 1, trip.getStartDate().plusDays(i)));
        }
        return itinerary;
    }

    @Transactional(readOnly = true)
    public List<Itinerary> listForTrip(UUID tripId, UUID userId) {
        tripService.getForItineraryAccess(tripId, userId);
        return itineraries.findByTripIdOrderByVersionDesc(tripId);
    }

    @Transactional(readOnly = true)
    public Itinerary getItinerary(UUID itineraryId, UUID userId) {
        Itinerary itinerary = findItinerary(itineraryId);
        tripService.getForItineraryAccess(itinerary.getTripId(), userId);
        return itinerary;
    }

    @Transactional(readOnly = true)
    public List<ItineraryDay> getDays(UUID itineraryId, UUID userId) {
        getItinerary(itineraryId, userId);
        return days.findByItineraryIdOrderByDayNumber(itineraryId);
    }

    @Transactional(readOnly = true)
    public List<ItineraryItem> getItems(UUID dayId, UUID userId) {
        ItineraryDay day = requireDayAccess(dayId, userId);
        return items.findByItineraryDayIdOrderBySortOrder(day.getId());
    }

    /** Deactivates every other version for the trip, activates this one, marks the trip READY. */
    @Transactional
    public Itinerary activate(UUID itineraryId, UUID userId) {
        Itinerary itinerary = getItinerary(itineraryId, userId);
        itineraries.deactivateAllForTrip(itinerary.getTripId());
        itinerary.setActive(true);
        itineraries.save(itinerary);
        tripService.setStatus(itinerary.getTripId(), TripStatus.READY);
        return itinerary;
    }

    @Transactional
    public ItineraryDay updateDay(UUID dayId, UUID userId, String theme, String notes) {
        ItineraryDay day = requireDayAccess(dayId, userId);
        if (theme != null) day.setTheme(theme);
        if (notes != null) day.setNotes(notes);
        return days.save(day);
    }

    @Transactional
    public ItineraryItem addItem(UUID dayId, UUID userId, String title, ItemType itemType,
                                 String description, LocalTime startTime, LocalTime endTime) {
        ItineraryDay day = requireDayAccess(dayId, userId);
        int nextOrder = items.findByItineraryDayIdOrderBySortOrder(dayId).size();
        ItineraryItem item = ItineraryItem.create(dayId, nextOrder, title, itemType);
        item.setDescription(description);
        item.setStartTime(startTime);
        item.setEndTime(endTime);
        item.setUserModified(true);
        return items.save(item);
    }

    @Transactional
    public ItineraryItem updateItem(UUID itemId, UUID userId, String title, String description,
                                    LocalTime startTime, LocalTime endTime, String locationName) {
        ItineraryItem item = requireItemAccess(itemId, userId);
        if (title != null) item.setTitle(title);
        if (description != null) item.setDescription(description);
        if (startTime != null) item.setStartTime(startTime);
        if (endTime != null) item.setEndTime(endTime);
        if (locationName != null) item.setLocationName(locationName);
        item.setUserModified(true);
        return items.save(item);
    }

    @Transactional
    public void deleteItem(UUID itemId, UUID userId) {
        ItineraryItem item = requireItemAccess(itemId, userId);
        items.delete(item);
    }

    /** Reassigns sortOrder to match the given full ordering of item ids for the day. */
    @Transactional
    public void reorderItems(UUID dayId, UUID userId, List<UUID> orderedItemIds) {
        ItineraryDay day = requireDayAccess(dayId, userId);
        List<ItineraryItem> current = items.findByItineraryDayIdOrderBySortOrder(day.getId());

        if (current.size() != orderedItemIds.size()
                || !current.stream().map(ItineraryItem::getId).allMatch(orderedItemIds::contains)) {
            throw new ValidationException("Reorder list must contain exactly the day's existing items");
        }

        for (int i = 0; i < orderedItemIds.size(); i++) {
            UUID id = orderedItemIds.get(i);
            ItineraryItem item = current.stream().filter(it -> it.getId().equals(id)).findFirst().orElseThrow();
            item.setSortOrder(i);
            items.save(item);
        }
    }

    private Itinerary findItinerary(UUID itineraryId) {
        return itineraries.findById(itineraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary not found: " + itineraryId));
    }

    private ItineraryDay requireDayAccess(UUID dayId, UUID userId) {
        ItineraryDay day = days.findById(dayId)
                .orElseThrow(() -> new ResourceNotFoundException("Day not found: " + dayId));
        getItinerary(day.getItineraryId(), userId);
        return day;
    }

    private ItineraryItem requireItemAccess(UUID itemId, UUID userId) {
        ItineraryItem item = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));
        requireDayAccess(item.getItineraryDayId(), userId);
        return item;
    }
}
