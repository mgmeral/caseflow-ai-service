package com.caseflow.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseMatch {
    private String sourceId;
    private String sourceType;
    private String title;
    private String snippet;
    private Double score;
    private Map<String, Object> metadata;
}
