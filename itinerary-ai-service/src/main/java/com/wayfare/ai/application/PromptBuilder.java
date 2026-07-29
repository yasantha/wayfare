package com.wayfare.ai.application;

import com.wayfare.ai.domain.PromptTemplate;
import com.wayfare.ai.infrastructure.client.CatalogClient.ActivityView;
import com.wayfare.ai.repository.PromptTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/** Fills the active {@link PromptTemplate} with a {@link GenerationContext} (design §7.6 versioning). */
@Service
public class PromptBuilder {

    private static final String TEMPLATE_NAME = "itinerary-generation";

    private final PromptTemplateRepository templates;

    public PromptBuilder(PromptTemplateRepository templates) {
        this.templates = templates;
    }

    public PromptTemplate activeTemplate() {
        return templates.findFirstByNameAndActiveTrueOrderByVersionDesc(TEMPLATE_NAME)
                .orElseThrow(() -> new IllegalStateException("No active prompt template: " + TEMPLATE_NAME));
    }

    public String systemPrompt(PromptTemplate template) {
        return template.getSystemPrompt();
    }

    public String userPrompt(PromptTemplate template, GenerationContext ctx) {
        String shortlist = ctx.shortlist().isEmpty()
                ? "(none available — invent plausible free-text items)"
                : ctx.shortlist().stream()
                        .map(a -> "%s | %s | %s | $%s | %dmin".formatted(
                                a.id(), a.name(), a.category(), a.estimatedCostUsd(), a.estimatedDurationMinutes()))
                        .collect(Collectors.joining("; "));

        var prefs = ctx.preferences();
        return template.getUserTemplate()
                .replace("{destinationName}", ctx.destinationName())
                .replace("{startDate}", ctx.startDate().toString())
                .replace("{endDate}", ctx.endDate().toString())
                .replace("{dayCount}", String.valueOf(ctx.dayCount()))
                .replace("{travelerCount}", String.valueOf(ctx.travelerCount()))
                .replace("{budgetAmount}", String.valueOf(ctx.budgetAmount()))
                .replace("{budgetCurrency}", String.valueOf(ctx.budgetCurrency()))
                .replace("{travelStyle}", String.valueOf(prefs.travelStyle()))
                .replace("{pace}", String.valueOf(prefs.pace()))
                .replace("{interests}", String.join(",", prefs.interests()))
                .replace("{avoidTags}", String.join(",", prefs.avoidTags()))
                .replace("{activityShortlist}", shortlist);
    }
}
