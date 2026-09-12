package com.caseflow.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured summary of a support ticket.
 * Fields populated by the LLM (summary, customerIntent, etc.) are deserialized from the model response.
 * Metadata fields (correlationId, model, latencyMs, etc.) are set by the service layer, not the LLM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketSummaryResponse {

    // ── Traceability (set by service, not LLM) ────────────────────────────
    private String correlationId;
    private String model;
    private String promptVersion;
    private String generatedAt;
    private Long latencyMs;
    private String ticketId;

    // ── LLM-generated content ─────────────────────────────────────────────
    private String summary;
    private String customerIntent;
    private List<String> keyPoints;
    private List<String> riskSignals;
    private String suggestedNextStep;
    private Double confidence;

    /**
     * References to specific messages or notes the summary is grounded in.
     * Empty if the model did not cite sources.
     */
    private List<String> citations;

    /**
     * Warnings about parse failures, missing context, or low-confidence output.
     * Always check this field before relying on the content.
     */
    private List<String> warnings;
}
