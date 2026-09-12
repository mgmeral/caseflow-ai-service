package com.caseflow.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Policy guidance grounded in retrieved policy documents.
 * If no policy documents were found for the query, {@code guidance} will contain an
 * explicit "no relevant policy found" message rather than a hallucinated answer.
 * Always inspect {@code warnings} and {@code citations} to understand confidence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyGuidanceResponse {

    // ── Traceability (set by service, not LLM) ────────────────────────────
    private String requestId;
    private String model;
    private String promptVersion;
    private String generatedAt;
    private Long latencyMs;
    private String ticketId;

    // ── LLM-generated content ─────────────────────────────────────────────
    private String answer;
    private List<String> recommendedActions;
    private Double confidence;

    /**
     * Source policy documents the answer is grounded in.
     * Empty if no policy documents were retrieved — in which case {@code answer}
     * will state that no relevant policy was found.
     */
    private List<PolicyReference> policyReferences;

    /**
     * Warnings about empty retrieval, ambiguous policy, or low confidence.
     * If non-empty, do not surface the guidance without agent review.
     */
    private List<String> warnings;
}
