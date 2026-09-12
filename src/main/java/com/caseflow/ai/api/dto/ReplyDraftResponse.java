package com.caseflow.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Suggested reply draft for a support ticket.
 * This is a suggestion only — caseflow-be decides whether and how to surface it to agents.
 * This service never sends email or mutates ticket state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReplyDraftResponse {

    // ── Traceability (set by service, not LLM) ────────────────────────────
    private String correlationId;
    private String model;
    private String promptVersion;
    private String generatedAt;
    private Long latencyMs;
    private String ticketId;

    // ── LLM-generated content ─────────────────────────────────────────────
    private String suggestedSubject;
    private String suggestedBody;
    private String reasoningSummary;
    private String tone;
    private List<String> suggestedTags;
    private String suggestedPriority;
    private Double confidence;

    /**
     * Warnings about parse failures, missing policy context, or constraint violations.
     * Always check this field before surfacing a draft to an agent.
     */
    private List<String> warnings;
}
