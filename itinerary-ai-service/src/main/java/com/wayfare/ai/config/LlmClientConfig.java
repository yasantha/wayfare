package com.wayfare.ai.config;

import com.wayfare.ai.application.llm.GeminiLlmClient;
import com.wayfare.ai.application.llm.GroqLlmClient;
import com.wayfare.ai.application.llm.LlmClient;
import com.wayfare.ai.application.llm.OllamaLlmClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the live provider adapter from {@code LLM_PROVIDER} (design §7.6:
 * swapping providers touches one class in one service — here, one branch of
 * this switch). Only reached when {@code DEMO_MODE=false}; demo mode bypasses
 * this bean entirely via {@link com.wayfare.ai.application.DemoItineraryBuilder}.
 */
@Configuration
@EnableConfigurationProperties({LlmProperties.class, QuotaProperties.class})
public class LlmClientConfig {

    @Bean
    public LlmClient llmClient(LlmProperties props) {
        return switch (props.provider().toLowerCase()) {
            case "gemini" -> new GeminiLlmClient(props);
            case "ollama" -> new OllamaLlmClient(props);
            default -> new GroqLlmClient(props);
        };
    }
}
