package com.caseflow.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarCasesRequest {
    @NotBlank
    private String queryText;
    private String customerName;
    private List<String> tags;
    @Builder.Default
    private Integer topK = 5;
    private SimilarCasesFilters filters;
}
