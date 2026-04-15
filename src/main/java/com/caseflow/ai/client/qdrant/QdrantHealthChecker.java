package com.caseflow.ai.client.qdrant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class QdrantHealthChecker {

    @Value("${spring.ai.vectorstore.qdrant.host}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port}")
    private int qdrantPort;

    private final RestTemplate restTemplate;

    public QdrantHealthChecker() {
        this.restTemplate = new RestTemplate();
    }

    public boolean isReachable() {
        try {
            restTemplate.getForEntity("http://" + qdrantHost + ":" + qdrantPort + "/healthz", String.class);
            return true;
        } catch (Exception e) {
            log.warn("Qdrant not reachable: {}", e.getMessage());
            return false;
        }
    }
}
