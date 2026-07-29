package com.wayfare.trip.api;

import com.wayfare.commons.web.PageResponse;
import com.wayfare.trip.api.dto.CreateTripRequest;
import com.wayfare.trip.api.dto.TripView;
import com.wayfare.trip.api.dto.UpdateTripRequest;
import com.wayfare.trip.application.TripService;
import com.wayfare.trip.domain.Trip;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Trip CRUD. Identity always comes from the validated JWT subject (design §6.3). */
@RestController
@RequestMapping("/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripView> create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody CreateTripRequest req) {
        Trip trip = tripService.create(userId(jwt), req.title(), req.destinationId(), req.startDate(),
                req.endDate(), req.travelerCount(), req.budgetAmount(), req.budgetCurrency());
        return ResponseEntity.status(HttpStatus.CREATED).body(TripView.from(trip));
    }

    @GetMapping
    public PageResponse<TripView> list(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return PageResponse.from(tripService.list(userId(jwt), pageable).map(TripView::from));
    }

    @GetMapping("/{id}")
    public TripView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return TripView.from(tripService.get(id, userId(jwt)));
    }

    @PatchMapping("/{id}")
    public TripView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                           @RequestBody UpdateTripRequest req) {
        Trip trip = tripService.update(id, userId(jwt), req.title(), req.startDate(), req.endDate(),
                req.travelerCount(), req.budgetAmount(), req.budgetCurrency());
        return TripView.from(trip);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        tripService.delete(id, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
