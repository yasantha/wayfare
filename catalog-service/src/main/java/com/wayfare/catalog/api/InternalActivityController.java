package com.wayfare.catalog.api;

import com.wayfare.catalog.api.dto.ActivityView;
import com.wayfare.catalog.api.dto.ShortlistRequest;
import com.wayfare.catalog.application.CatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bulk activity fetch for Itinerary AI's prompt grounding (design §7.3).
 * Blocked at the gateway from outside; reachable only inside the cluster.
 */
@RestController
@RequestMapping("/internal/activities")
public class InternalActivityController {

    private final CatalogService catalogService;

    public InternalActivityController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/shortlist")
    public List<ActivityView> shortlist(@Valid @RequestBody ShortlistRequest req) {
        int limit = req.limit() != null ? req.limit() : 50;
        return catalogService.shortlist(req.destinationId(), req.maxCostUsd(), limit)
                .stream().map(ActivityView::from).toList();
    }
}
