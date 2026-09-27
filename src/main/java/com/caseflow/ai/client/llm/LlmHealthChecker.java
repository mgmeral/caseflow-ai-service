package com.caseflow.ai.client.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Checks the OpenAI-compatible chat and embedding servers via {@code GET /v1/models},
 * which llama-server, vLLM, Ollama and hosted providers all expose.
 */
@Component
@Slf4j
public class LlmHealthChecker {

    private static final String PLACEHOLDER_KEY = "not-needed";

    @Value("${spring.ai.openai.chat.base-url}")
    private String chatBaseUrl;

    @Value("${spring.ai.openai.embedding.base-url}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public LlmHealthChecker() {
        this.restTemplate = new RestTemplate();
    }

    public boolean isChatReachable() {
        return isReachable(chatBaseUrl, "chat");
    }

    public boolean isEmbeddingReachable() {
        return isReachable(embeddingBaseUrl, "embedding");
    }

    private boolean isReachable(String baseUrl, String role) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isBlank() && !PLACEHOLDER_KEY.equals(apiKey)) {
                headers.setBearerAuth(apiKey);
            }
            restTemplate.exchange(baseUrl + "/v1/models", HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return true;
        } catch (Exception e) {
            log.warn("LLM {} server not reachable at {}: {}", role, baseUrl, e.getMessage());
            return false;
        }
    }
}
