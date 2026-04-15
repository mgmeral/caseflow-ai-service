package com.caseflow.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyGuidanceResponse {
    private String ticketId;
    private String answer;
    private List<String> recommendedActions;
    private List<PolicyReference> policyReferences;
    private Double confidence;
    private List<String> warnings;
}
