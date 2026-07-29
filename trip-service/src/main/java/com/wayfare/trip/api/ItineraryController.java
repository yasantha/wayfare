package com.wayfare.trip.api;

import com.wayfare.trip.api.dto.AddItemRequest;
import com.wayfare.trip.api.dto.ItineraryDayView;
import com.wayfare.trip.api.dto.ItineraryDetailView;
import com.wayfare.trip.api.dto.ItineraryItemView;
import com.wayfare.trip.api.dto.ItineraryView;
import com.wayfare.trip.api.dto.ReorderItemsRequest;
import com.wayfare.trip.api.dto.UpdateDayRequest;
import com.wayfare.trip.api.dto.UpdateItemRequest;
import com.wayfare.trip.application.ItineraryService;
import com.wayfare.trip.domain.Itinerary;
import com.wayfare.trip.domain.ItineraryDay;
import com.wayfare.trip.domain.ItineraryItem;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.wayfare.trip.api.TripController.userId;

@RestController
public class ItineraryController {

    private final ItineraryService itineraryService;

    public ItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @GetMapping("/trips/{tripId}/itineraries")
    public List<ItineraryView> listForTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId) {
        return itineraryService.listForTrip(tripId, userId(jwt)).stream().map(ItineraryView::from).toList();
    }

    @PostMapping("/trips/{tripId}/itineraries")
    public ResponseEntity<ItineraryView> createManual(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID tripId) {
        Itinerary itinerary = itineraryService.createManualVersion(tripId, userId(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(ItineraryView.from(itinerary));
    }

    @GetMapping("/itineraries/{id}")
    public ItineraryDetailView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID uid = userId(jwt);
        Itinerary itinerary = itineraryService.getItinerary(id, uid);
        List<ItineraryDetailView.DayDetail> days = itineraryService.getDays(id, uid).stream()
                .map(day -> toDayDetail(day, itineraryService.getItems(day.getId(), uid)))
                .toList();
        return ItineraryDetailView.from(itinerary, days);
    }

    @PostMapping("/itineraries/{id}/activate")
    public ItineraryView activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ItineraryView.from(itineraryService.activate(id, userId(jwt)));
    }

    @PatchMapping("/itineraries/{itineraryId}/days/{dayId}")
    public ItineraryDayView updateDay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itineraryId,
                                      @PathVariable UUID dayId, @RequestBody UpdateDayRequest req) {
        ItineraryDay day = itineraryService.updateDay(dayId, userId(jwt), req.theme(), req.notes());
        return ItineraryDayView.from(day);
    }

    @PostMapping("/itineraries/{itineraryId}/days/{dayId}/items")
    public ResponseEntity<ItineraryItemView> addItem(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID itineraryId, @PathVariable UUID dayId,
                                                     @Valid @RequestBody AddItemRequest req) {
        ItineraryItem item = itineraryService.addItem(dayId, userId(jwt), req.title(), req.itemType(),
                req.description(), req.startTime(), req.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItineraryItemView.from(item));
    }

    @PutMapping("/itineraries/{itineraryId}/days/{dayId}/items/order")
    public ResponseEntity<Void> reorderItems(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itineraryId,
                                             @PathVariable UUID dayId, @Valid @RequestBody ReorderItemsRequest req) {
        itineraryService.reorderItems(dayId, userId(jwt), req.itemIds());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/itinerary-items/{itemId}")
    public ItineraryItemView updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId,
                                        @RequestBody UpdateItemRequest req) {
        ItineraryItem item = itineraryService.updateItem(itemId, userId(jwt), req.title(), req.description(),
                req.startTime(), req.endTime(), req.locationName());
        return ItineraryItemView.from(item);
    }

    @DeleteMapping("/itinerary-items/{itemId}")
    public ResponseEntity<Void> deleteItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId) {
        itineraryService.deleteItem(itemId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static ItineraryDetailView.DayDetail toDayDetail(ItineraryDay day, List<ItineraryItem> items) {
        return new ItineraryDetailView.DayDetail(day.getId(), day.getDayNumber(), day.getDate(), day.getTheme(),
                day.getNotes(), day.getEstimatedCost(), items.stream().map(ItineraryItemView::from).toList());
    }
}
