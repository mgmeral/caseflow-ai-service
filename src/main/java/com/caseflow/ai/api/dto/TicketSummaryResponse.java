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
public class TicketSummaryResponse {
    private String ticketId;
    private String summary;
    private String customerIntent;
    private List<String> keyPoints;
    private List<String> riskSignals;
    private String suggestedNextStep;
    private Double confidence;
    private List<String> citations;
}
