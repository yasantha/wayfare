package com.wayfare.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        boolean demoMode,
        String provider,
        Duration readTimeout,
        Groq groq,
        Gemini gemini,
        Ollama ollama
) {
    public record Groq(String baseUrl, String apiKey, String model) {
    }

    public record Gemini(String apiKey, String model) {
    }

    public record Ollama(String baseUrl, String model) {
    }
}
