package com.wayfare.ai.application.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.wayfare.ai.config.LlmProperties;
import com.wayfare.commons.error.Exceptions.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/** Fully local, no API key, no cost (design §7.6: offline provider option). */
public class OllamaLlmClient implements LlmClient {

    private final WebClient webClient;
    private final String model;

    public OllamaLlmClient(LlmProperties props) {
        this.webClient = WebClient.builder().baseUrl(props.ollama().baseUrl()).build();
        this.model = props.ollama().model();
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
        return "ollama:" + model;
    }

    private LlmResponse call(List<Map<String, String>> messages) {
        try {
            JsonNode response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(Map.of("model", model, "messages", messages, "format", "json", "stream", false))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String content = response.path("message").path("content").asText();
            int promptTokens = response.path("prompt_eval_count").asInt(0);
            int completionTokens = response.path("eval_count").asInt(0);
            return new LlmResponse(content, promptTokens, completionTokens);
        } catch (Exception e) {
            throw new ExternalServiceException("Ollama API call failed", e);
        }
    }
}
