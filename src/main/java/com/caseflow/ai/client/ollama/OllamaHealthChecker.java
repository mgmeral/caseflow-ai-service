package com.caseflow.ai.client.ollama;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class OllamaHealthChecker {

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    private final RestTemplate restTemplate;

    public OllamaHealthChecker() {
        this.restTemplate = new RestTemplate();
    }

    public boolean isReachable() {
        try {
            restTemplate.getForEntity(ollamaBaseUrl + "/api/tags", String.class);
            return true;
        } catch (Exception e) {
            log.warn("Ollama not reachable: {}", e.getMessage());
            return false;
        }
    }
}
