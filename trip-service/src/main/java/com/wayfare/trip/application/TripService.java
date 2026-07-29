package com.wayfare.trip.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.commons.correlation.Correlation;
import com.wayfare.commons.error.Exceptions.ForbiddenException;
import com.wayfare.commons.error.Exceptions.ResourceNotFoundException;
import com.wayfare.commons.events.EventEnvelope;
import com.wayfare.trip.domain.OutboxEvent;
import com.wayfare.trip.domain.Trip;
import com.wayfare.trip.domain.TripStatus;
import com.wayfare.trip.infrastructure.client.CatalogClient;
import com.wayfare.trip.repository.OutboxRepository;
import com.wayfare.trip.repository.TripRepository;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Trip CRUD. Every read/write is resolved through the authenticated user id —
 * never trust a path variable alone — so a valid token for user A can never
 * reach user B's trip by guessing a UUID (design §6.3, the most common
 * vulnerability class in this kind of app).
 */
@Service
public class TripService {

    private final TripRepository trips;
    private final OutboxRepository outbox;
    private final CatalogClient catalogClient;
    private final ObjectMapper objectMapper;

    public TripService(TripRepository trips, OutboxRepository outbox,
                       CatalogClient catalogClient, ObjectMapper objectMapper) {
        this.trips = trips;
        this.outbox = outbox;
        this.catalogClient = catalogClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Trip create(UUID userId, String title, UUID destinationId, LocalDate startDate, LocalDate endDate,
                       int travelerCount, BigDecimal budgetAmount, String budgetCurrency) {
        String snapshot = destinationId != null ? snapshotOf(destinationId) : null;

        Trip trip = Trip.create(userId, title, destinationId, snapshot, startDate, endDate,
                travelerCount, budgetAmount, budgetCurrency);
        trips.save(trip);

        outbox.save(OutboxEvent.of("trip", trip.getId().toString(), "trip.created",
                tripCreatedPayload(trip), MDC.get(Correlation.MDC_KEY)));

        return trip;
    }

    @Transactional(readOnly = true)
    public Trip get(UUID tripId, UUID userId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        requireOwnership(trip, userId);
        return trip;
    }

    @Transactional(readOnly = true)
    public Page<Trip> list(UUID userId, Pageable pageable) {
        return trips.findByUserId(userId, pageable);
    }

    @Transactional
    public Trip update(UUID tripId, UUID userId, String title, LocalDate startDate, LocalDate endDate,
                       Integer travelerCount, BigDecimal budgetAmount, String budgetCurrency) {
        Trip trip = get(tripId, userId);
        if (title != null) trip.setTitle(title);
        if (startDate != null) trip.setStartDate(startDate);
        if (endDate != null) trip.setEndDate(endDate);
        if (travelerCount != null) trip.setTravelerCount(travelerCount);
        if (budgetAmount != null) trip.setBudgetAmount(budgetAmount);
        if (budgetCurrency != null) trip.setBudgetCurrency(budgetCurrency);
        return trips.save(trip);
    }

    @Transactional
    public void delete(UUID tripId, UUID userId) {
        Trip trip = get(tripId, userId);
        trips.delete(trip);
    }

    /** Package-visible: used by ItineraryService to resolve+check ownership via the parent trip. */
    Trip getForItineraryAccess(UUID tripId, UUID userId) {
        return get(tripId, userId);
    }

    /**
     * Called by Itinerary AI (Phase 5) right before starting generation, and by
     * the generation-result consumer on success/failure (design §4.4 saga).
     */
    @Transactional
    public void setStatus(UUID tripId, TripStatus status) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        trip.setStatus(status);
        trips.save(trip);
    }

    private void requireOwnership(Trip trip, UUID userId) {
        if (!trip.isOwnedBy(userId)) {
            throw new ForbiddenException("You do not have access to this trip");
        }
    }

    private String snapshotOf(UUID destinationId) {
        CatalogClient.DestinationSnapshot d = catalogClient.fetchDestination(destinationId);
        try {
            return objectMapper.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize destination snapshot", e);
        }
    }

    private String tripCreatedPayload(Trip trip) {
        try {
            Map<String, Object> envelope = EventEnvelope.wrap("trip.created", 1,
                    MDC.get(Correlation.MDC_KEY),
                    Map.of("tripId", trip.getId().toString(),
                            "userId", trip.getUserId().toString(),
                            "destinationId", trip.getDestinationId() != null ? trip.getDestinationId().toString() : "",
                            "startDate", trip.getStartDate().toString(),
                            "endDate", trip.getEndDate().toString()));
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trip.created payload", e);
        }
    }
}
