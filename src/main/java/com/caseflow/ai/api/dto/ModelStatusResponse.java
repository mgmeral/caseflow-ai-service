package com.caseflow.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelStatusResponse {
    private boolean ollamaReachable;
    private boolean qdrantReachable;
    private String chatModel;
    private String embeddingModel;
}
