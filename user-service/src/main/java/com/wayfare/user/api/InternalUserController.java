package com.wayfare.user.api;

import com.wayfare.user.api.dto.PreferencesView;
import com.wayfare.user.application.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service endpoint used by Itinerary AI to fetch a user's preferences
 * for prompt grounding. Blocked at the gateway from outside; reachable only
 * inside the cluster (design §6.2). Client-credentials auth is a hardening step.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}/preferences")
    public PreferencesView preferences(@PathVariable UUID id) {
        return PreferencesView.from(userService.getPreferencesOrEmpty(id));
    }
}
