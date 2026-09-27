package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.HealthReadyResponse;
import com.caseflow.ai.api.dto.ModelStatusResponse;
import com.caseflow.ai.client.llm.LlmHealthChecker;
import com.caseflow.ai.client.qdrant.QdrantHealthChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final LlmHealthChecker llmHealthChecker;
    private final QdrantHealthChecker qdrantHealthChecker;

    @Value("${spring.ai.openai.chat.options.model}")
    private String chatModel;

    @Value("${spring.ai.openai.embedding.options.model}")
    private String embeddingModel;

    public ModelStatusResponse getModelStatus() {
        return ModelStatusResponse.builder()
                .chatModelReachable(llmHealthChecker.isChatReachable())
                .embeddingModelReachable(llmHealthChecker.isEmbeddingReachable())
                .qdrantReachable(qdrantHealthChecker.isReachable())
                .chatModel(chatModel)
                .embeddingModel(embeddingModel)
                .build();
    }

    public HealthReadyResponse getReadiness() {
        boolean ready = llmHealthChecker.isChatReachable()
                && llmHealthChecker.isEmbeddingReachable()
                && qdrantHealthChecker.isReachable();
        return HealthReadyResponse.builder()
                .ready(ready)
                .status(ready ? "UP" : "DEGRADED")
                .build();
    }
}
