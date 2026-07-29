package com.wayfare.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wayfare.ai.application.llm.LlmClient;
import com.wayfare.ai.config.LlmProperties;
import com.wayfare.ai.domain.GenerationRequest;
import com.wayfare.ai.domain.PromptTemplate;
import com.wayfare.ai.infrastructure.client.CatalogClient;
import com.wayfare.ai.infrastructure.client.TripClient;
import com.wayfare.ai.infrastructure.client.UserClient;
import com.wayfare.ai.repository.GenerationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The actual generation work (design §7.2: async by necessity — 10-40s of
 * external calls). Runs off the request thread on a bounded executor (design
 * §7.2 recommends virtual threads for this pure-I/O workload). Never throws
 * back to its caller — every path ends in {@code recordSuccess} or
 * {@code recordFailure}, since nothing is listening synchronously.
 */
@Component
public class GenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);
    private static final int MAX_SHORTLIST = 50;

    private final GenerationRequestRepository requests;
    private final CatalogClient catalogClient;
    private final UserClient userClient;
    private final TripClient tripClient;
    private final PromptBuilder promptBuilder;
    private final DemoItineraryBuilder demoItineraryBuilder;
    private final LlmClient llmClient;
    private final ItineraryValidator validator;
    private final GenerationOutcomeService outcomeService;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    public GenerationWorker(GenerationRequestRepository requests, CatalogClient catalogClient,
                            UserClient userClient, TripClient tripClient, PromptBuilder promptBuilder,
                            DemoItineraryBuilder demoItineraryBuilder, LlmClient llmClient,
                            ItineraryValidator validator, GenerationOutcomeService outcomeService,
                            LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.requests = requests;
        this.catalogClient = catalogClient;
        this.userClient = userClient;
        this.tripClient = tripClient;
        this.promptBuilder = promptBuilder;
        this.demoItineraryBuilder = demoItineraryBuilder;
        this.llmClient = llmClient;
        this.validator = validator;
        this.outcomeService = outcomeService;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    @Async
    public void process(UUID requestId, GenerationParams params, String correlationId) {
        outcomeService.markInProgress(requestId);
        long startedAt = System.currentTimeMillis();

        try {
            GenerationContext ctx = buildContext(requestId, params);
            tripClient.markGenerating(ctx.tripId());

            String model;
            String rawJson;
            int promptTokens = 0;
            int completionTokens = 0;

            if (llmProperties.demoMode()) {
                model = "demo-mode";
                rawJson = demoItineraryBuilder.build(ctx);
            } else {
                PromptTemplate template = promptBuilder.activeTemplate();
                String systemPrompt = promptBuilder.systemPrompt(template);
                String userPrompt = promptBuilder.userPrompt(template, ctx);
                LlmClient.LlmResponse response = llmClient.generate(systemPrompt, userPrompt);
                model = llmClient.providerName();
                rawJson = response.rawJson();
                promptTokens = response.promptTokens();
                completionTokens = response.completionTokens();
            }

            ObjectNode parsed = parseOrNull(rawJson);
            ItineraryValidator.ValidationResult result = parsed != null
                    ? validator.validate(parsed, ctx)
                    : new ItineraryValidator.ValidationResult(false, List.of("Response was not valid JSON"), null);

            if (!result.valid() && !llmProperties.demoMode()) {
                // One repair round-trip (design §7.5), never more.
                PromptTemplate template = promptBuilder.activeTemplate();
                String systemPrompt = promptBuilder.systemPrompt(template);
                LlmClient.LlmResponse repaired = llmClient.repair(systemPrompt, rawJson,
                        String.join("; ", result.errors()));
                rawJson = repaired.rawJson();
                promptTokens += repaired.promptTokens();
                completionTokens += repaired.completionTokens();

                ObjectNode reparsed = parseOrNull(rawJson);
                result = reparsed != null
                        ? validator.validate(reparsed, ctx)
                        : new ItineraryValidator.ValidationResult(false, List.of("Repair response was not valid JSON"), null);
            }

            int latencyMs = (int) (System.currentTimeMillis() - startedAt);

            if (result.valid()) {
                outcomeService.recordSuccess(requestId, model, promptTokens, completionTokens,
                        latencyMs, rawJson, result.reconciled().toString(), correlationId);
                log.info("Generation {} succeeded ({} days, model={})", requestId, ctx.dayCount(), model);
            } else {
                outcomeService.recordFailure(requestId, "VALIDATION_FAILED",
                        String.join("; ", result.errors()), correlationId);
                log.warn("Generation {} failed validation: {}", requestId, result.errors());
            }
        } catch (Exception e) {
            log.error("Generation {} failed with an unexpected error", requestId, e);
            outcomeService.recordFailure(requestId, "GENERATION_ERROR", e.getMessage(), correlationId);
        }
    }

    private GenerationContext buildContext(UUID requestId, GenerationParams params) {
        GenerationRequest request = requests.findById(requestId).orElseThrow();
        String destinationName = params.destinationId() != null
                ? catalogClient.fetchDestinationName(params.destinationId())
                : "the destination";
        List<CatalogClient.ActivityView> shortlist = params.destinationId() != null
                ? catalogClient.fetchShortlist(params.destinationId(), null, MAX_SHORTLIST)
                : List.of();
        UserClient.PreferencesView preferences = userClient.fetchPreferences(request.getUserId());

        return new GenerationContext(request.getTripId(), request.getUserId(), params.destinationId(),
                destinationName, params.startDate(), params.endDate(), params.travelerCount(),
                params.budgetAmount(), params.budgetCurrency(), preferences, shortlist);
    }

    private ObjectNode parseOrNull(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** Trip parameters carried directly on the generate request (design ADR-011). */
    public record GenerationParams(
            UUID destinationId, java.time.LocalDate startDate, java.time.LocalDate endDate,
            int travelerCount, BigDecimal budgetAmount, String budgetCurrency
    ) {
    }
}
