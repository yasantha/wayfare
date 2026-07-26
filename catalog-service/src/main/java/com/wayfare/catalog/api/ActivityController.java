package com.wayfare.catalog.api;

import com.wayfare.catalog.api.dto.ActivityView;
import com.wayfare.catalog.application.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ActivityController {

    private final CatalogService catalogService;

    public ActivityController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/activities/{id}")
    public ActivityView get(@PathVariable UUID id) {
        return ActivityView.from(catalogService.getActivity(id));
    }
}
