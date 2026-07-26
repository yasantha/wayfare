package com.wayfare.catalog.api;

import com.wayfare.catalog.api.dto.ActivityView;
import com.wayfare.catalog.api.dto.DestinationView;
import com.wayfare.catalog.application.CatalogService;
import com.wayfare.commons.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class DestinationController {

    private final CatalogService catalogService;

    public DestinationController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/destinations")
    public PageResponse<DestinationView> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String countryCode,
                                                Pageable pageable) {
        return PageResponse.from(catalogService.searchDestinations(q, countryCode, pageable)
                .map(DestinationView::from));
    }

    @GetMapping("/destinations/{id}")
    public DestinationView get(@PathVariable UUID id) {
        return DestinationView.from(catalogService.getDestination(id));
    }

    @GetMapping("/destinations/{id}/activities")
    public PageResponse<ActivityView> activities(@PathVariable UUID id, Pageable pageable) {
        return PageResponse.from(catalogService.getActivitiesForDestination(id, pageable)
                .map(ActivityView::from));
    }
}
