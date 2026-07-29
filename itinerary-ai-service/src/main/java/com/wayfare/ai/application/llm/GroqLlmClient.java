package com.wayfare.ai.application.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.wayfare.ai.config.LlmProperties;
import com.wayfare.commons.error.Exceptions.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat completions API (design §7.6: free, fast — the
 * default live provider). {@code response_format=json_object} guarantees
 * syntactically valid JSON; the system prompt carries the exact schema, and
 * {@link com.wayfare.ai.application.ItineraryValidator} catches semantic
 * issues (design §7.5) — this adapter never trusts shape beyond "valid JSON".
 */
public class GroqLlmClient implements LlmClient {

    private final WebClient webClient;
    private final String model;

    public GroqLlmClient(LlmProperties props) {
        this.webClient = WebClient.builder()
                .baseUrl(props.groq().baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.groq().apiKey())
                .build();
        this.model = props.groq().model();
    }

    @Override
    @Retry(name = "llm")
    @CircuitBreaker(name = "llm")
    public LlmResponse generate(String systemPrompt, String userPrompt) {
        return call(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
    }

    @Override
    @Retry(name = "llm")
    @CircuitBreaker(name = "llm")
    public LlmResponse repair(String systemPrompt, String previousResponseJson, String validationErrors) {
        String repairPrompt = "Your previous response failed validation with these errors: "
                + validationErrors + ". Here was your previous response: " + previousResponseJson
                + ". Return a corrected JSON object fixing every error, in the exact same shape.";
        return call(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", repairPrompt)));
    }

    @Override
    public String providerName() {
        return "groq:" + model;
    }

    private LlmResponse call(List<Map<String, String>> messages) {
        try {
            JsonNode response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(Map.of(
                            "model", model,
                            "messages", messages,
                            "response_format", Map.of("type", "json_object"),
                            "temperature", 0.7))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String content = response.path("choices").get(0).path("message").path("content").asText();
            int promptTokens = response.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = response.path("usage").path("completion_tokens").asInt(0);
            return new LlmResponse(content, promptTokens, completionTokens);
        } catch (Exception e) {
            throw new ExternalServiceException("Groq API call failed", e);
        }
    }
}
