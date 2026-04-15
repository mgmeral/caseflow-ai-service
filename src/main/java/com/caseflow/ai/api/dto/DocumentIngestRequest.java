package com.caseflow.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIngestRequest {
    @NotBlank
    private String sourceId;
    @NotBlank
    private String sourceType;
    @NotBlank
    private String title;
    @NotBlank
    private String text;
    private Map<String, Object> metadata;
}
