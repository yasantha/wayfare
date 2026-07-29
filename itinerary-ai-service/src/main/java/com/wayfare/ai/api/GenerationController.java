package com.wayfare.ai.api;

import com.wayfare.ai.api.dto.GenerateRequest;
import com.wayfare.ai.api.dto.GenerationRequestView;
import com.wayfare.ai.application.GenerationService;
import com.wayfare.ai.domain.GenerationRequest;
import com.wayfare.ai.repository.GenerationRequestRepository;
import com.wayfare.commons.correlation.Correlation;
import com.wayfare.commons.error.Exceptions.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * design §5.2 (Itinerary AI Service) + §7.1: the client hits this service
 * directly for generation, not Trip Service (design ADR-011).
 */
@RestController
public class GenerationController {

    private final GenerationService generationService;
    private final GenerationRequestRepository requests;

    public GenerationController(GenerationService generationService, GenerationRequestRepository requests) {
        this.generationService = generationService;
        this.requests = requests;
    }

    @PostMapping("/trips/{tripId}/itinerary:generate")
    public ResponseEntity<GenerationRequestView> generate(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable UUID tripId,
                                                          @Valid @RequestBody GenerateRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        GenerationRequest request = generationService.requestGeneration(tripId, userId, req.destinationId(),
                req.startDate(), req.endDate(), req.travelerCount(), req.budgetAmount(), req.budgetCurrency(),
                MDC.get(Correlation.MDC_KEY));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(GenerationRequestView.from(request));
    }

    @GetMapping("/generation-requests/{requestId}")
    public GenerationRequestView get(@PathVariable UUID requestId) {
        return requests.findById(requestId)
                .map(GenerationRequestView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Generation request not found: " + requestId));
    }

    @GetMapping("/trips/{tripId}/generation-status")
    public GenerationRequestView status(@PathVariable UUID tripId) {
        return requests.findFirstByTripIdOrderByCreatedAtDesc(tripId)
                .map(GenerationRequestView::from)
                .orElseThrow(() -> new ResourceNotFoundException("No generation requests for trip: " + tripId));
    }
}
