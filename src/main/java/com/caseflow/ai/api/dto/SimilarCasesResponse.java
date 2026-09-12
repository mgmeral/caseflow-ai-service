package com.caseflow.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resolved or known cases retrieved from the vector store that are semantically similar
 * to the queried ticket. Results are grounded in indexed content — not hallucinated.
 * An empty {@code items} list means no indexed cases matched. Ingest resolved tickets first.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimilarCasesResponse {

    // ── Traceability ──────────────────────────────────────────────────────
    private String requestId;
    private String model;
    private String promptVersion;
    private String generatedAt;
    private Long latencyMs;
    private String ticketId;

    // ── Retrieval results ─────────────────────────────────────────────────
    private List<CaseMatch> matches;

    /**
     * Warnings about empty retrieval, low similarity, or missing index coverage.
     * If non-empty, interpret the results with caution.
     */
    private List<String> warnings;
}
