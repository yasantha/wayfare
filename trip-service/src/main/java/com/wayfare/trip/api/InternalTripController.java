package com.wayfare.trip.api;

import com.wayfare.trip.application.TripService;
import com.wayfare.trip.domain.TripStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only (blocked at the gateway from outside, design §6.2).
 * Itinerary AI (Phase 5) calls this to flip a trip to GENERATING right before
 * starting work (design §4.4 saga step 1); steps 3-4 are handled by
 * {@code ItineraryGenerationEventConsumer} once generation completes.
 */
@RestController
@RequestMapping("/internal/trips")
public class InternalTripController {

    private final TripService tripService;

    public InternalTripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PatchMapping("/{id}/status")
    public void setStatus(@PathVariable UUID id, @RequestBody StatusRequest req) {
        tripService.setStatus(id, req.status());
    }

    public record StatusRequest(TripStatus status) {
    }
}
