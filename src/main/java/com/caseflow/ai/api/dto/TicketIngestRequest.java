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
    /** Stored on every chunk for scope filtering in similar-case search. Optional. */
    private String customerId;
    private String groupId;
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
