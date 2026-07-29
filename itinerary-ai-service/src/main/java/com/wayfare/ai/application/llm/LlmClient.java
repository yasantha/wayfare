package com.wayfare.ai.application.llm;

import com.wayfare.ai.application.GenerationContext;

/**
 * Provider port (design §7.6): swapping providers touches one adapter class,
 * never the orchestration logic. {@code repair} sends the model's own invalid
 * output back with the validation errors for a single correction round-trip
 * (design §7.5).
 */
public interface LlmClient {

    LlmResponse generate(String systemPrompt, String userPrompt);

    LlmResponse repair(String systemPrompt, String previousResponseJson, String validationErrors);

    /** Provider name recorded on the generation_request row for cost/traceability. */
    String providerName();

    record LlmResponse(String rawJson, int promptTokens, int completionTokens) {
    }
}
