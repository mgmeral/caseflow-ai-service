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

    // HTTP REST port (default 6333) — separate from the gRPC port (6334) used by Spring AI VectorStore
    @Value("${caseflow.ai.qdrant-http-port:6333}")
    private int qdrantHttpPort;

    private final RestTemplate restTemplate;

    public QdrantHealthChecker() {
        this.restTemplate = new RestTemplate();
    }

    public boolean isReachable() {
        try {
            restTemplate.getForEntity("http://" + qdrantHost + ":" + qdrantHttpPort + "/healthz", String.class);
            return true;
        } catch (Exception e) {
            log.warn("Qdrant not reachable: {}", e.getMessage());
            return false;
        }
    }
}
