package com.wayfare.ai.application.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.wayfare.ai.config.LlmProperties;
import com.wayfare.commons.error.Exceptions.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/** Google Gemini generateContent API — native JSON response mime type (design §7.4). */
public class GeminiLlmClient implements LlmClient {

    private final WebClient webClient;
    private final String model;
    private final String apiKey;

    public GeminiLlmClient(LlmProperties props) {
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
        this.model = props.gemini().model();
        this.apiKey = props.gemini().apiKey();
    }

    @Override
    @Retry(name = "llm")
    @CircuitBreaker(name = "llm")
    public LlmResponse generate(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt);
    }

    @Override
    @Retry(name = "llm")
    @CircuitBreaker(name = "llm")
    public LlmResponse repair(String systemPrompt, String previousResponseJson, String validationErrors) {
        String repairPrompt = "Your previous response failed validation with these errors: "
                + validationErrors + ". Here was your previous response: " + previousResponseJson
                + ". Return a corrected JSON object fixing every error, in the exact same shape.";
        return call(systemPrompt, repairPrompt);
    }

    @Override
    public String providerName() {
        return "gemini:" + model;
    }

    private LlmResponse call(String systemPrompt, String userPrompt) {
        try {
            JsonNode response = webClient.post()
                    .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                    .bodyValue(Map.of(
                            "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
                            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                            "generationConfig", Map.of("responseMimeType", "application/json")))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String content = response.path("candidates").get(0).path("content")
                    .path("parts").get(0).path("text").asText();
            int promptTokens = response.path("usageMetadata").path("promptTokenCount").asInt(0);
            int completionTokens = response.path("usageMetadata").path("candidatesTokenCount").asInt(0);
            return new LlmResponse(content, promptTokens, completionTokens);
        } catch (Exception e) {
            throw new ExternalServiceException("Gemini API call failed", e);
        }
    }
}
