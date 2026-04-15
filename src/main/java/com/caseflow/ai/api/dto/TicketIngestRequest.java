package com.caseflow.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketIngestRequest {
    @NotBlank
    private String sourceId;
    private String customerName;
    @NotBlank
    private String subject;
    @NotBlank
    private String body;
    private String resolutionSummary;
    private List<String> tags;
    @NotBlank
    private String status;
    private Map<String, Object> metadata;
}
