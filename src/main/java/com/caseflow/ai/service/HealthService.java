package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.HealthReadyResponse;
import com.caseflow.ai.api.dto.ModelStatusResponse;
import com.caseflow.ai.client.ollama.OllamaHealthChecker;
import com.caseflow.ai.client.qdrant.QdrantHealthChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final OllamaHealthChecker ollamaHealthChecker;
    private final QdrantHealthChecker qdrantHealthChecker;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String chatModel;

    @Value("${spring.ai.ollama.embedding.options.model}")
    private String embeddingModel;

    public ModelStatusResponse getModelStatus() {
        boolean ollamaReachable = ollamaHealthChecker.isReachable();
        boolean qdrantReachable = qdrantHealthChecker.isReachable();
        return ModelStatusResponse.builder()
                .ollamaReachable(ollamaReachable)
                .qdrantReachable(qdrantReachable)
                .chatModel(chatModel)
                .embeddingModel(embeddingModel)
                .build();
    }

    public HealthReadyResponse getReadiness() {
        boolean ollamaReachable = ollamaHealthChecker.isReachable();
        boolean qdrantReachable = qdrantHealthChecker.isReachable();
        boolean ready = ollamaReachable && qdrantReachable;
        return HealthReadyResponse.builder()
                .ready(ready)
                .status(ready ? "UP" : "DEGRADED")
                .build();
    }
}
