package com.wayfare.trip.application;

import com.wayfare.commons.error.Exceptions.ForbiddenException;
import com.wayfare.trip.domain.Trip;
import com.wayfare.trip.repository.ItineraryDayRepository;
import com.wayfare.trip.repository.ItineraryItemRepository;
import com.wayfare.trip.repository.ItineraryRepository;
import com.wayfare.trip.repository.OutboxRepository;
import com.wayfare.trip.repository.TripRepository;
import com.wayfare.trip.infrastructure.client.CatalogClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Ownership is the single most important property in this service (design
 * §6.3): a valid token for user A must never reach user B's trip. Tested at
 * the unit level with mocks (fast); AuthFlowIT-style end-to-end proof lives in
 * the Testcontainers IT.
 */
@ExtendWith(MockitoExtension.class)
class ItineraryServiceOwnershipTest {

    @Mock
    TripRepository tripRepository;
    @Mock
    OutboxRepository outboxRepository;
    @Mock
    CatalogClient catalogClient;
    @Mock
    ItineraryRepository itineraryRepository;
    @Mock
    ItineraryDayRepository dayRepository;
    @Mock
    ItineraryItemRepository itemRepository;

    @Test
    void getTrip_ownedByAnotherUser_throwsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        Trip trip = Trip.create(ownerId, "Kyoto trip", null,
                null, LocalDate.now(), LocalDate.now().plusDays(3), 2, null, null);
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        TripService tripService = new TripService(tripRepository, outboxRepository, catalogClient,
                new com.fasterxml.jackson.databind.ObjectMapper());

        assertThatThrownBy(() -> tripService.get(trip.getId(), attackerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createManualVersion_forTripOwnedByAnotherUser_throwsForbidden() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        Trip trip = Trip.create(ownerId, "Kyoto trip", null,
                null, LocalDate.now(), LocalDate.now().plusDays(3), 2, null, null);
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        TripService tripService = new TripService(tripRepository, outboxRepository, catalogClient,
                new com.fasterxml.jackson.databind.ObjectMapper());
        ItineraryService itineraryService = new ItineraryService(tripService, itineraryRepository,
                dayRepository, itemRepository);

        assertThatThrownBy(() -> itineraryService.createManualVersion(trip.getId(), attackerId))
                .isInstanceOf(ForbiddenException.class);
    }
}
