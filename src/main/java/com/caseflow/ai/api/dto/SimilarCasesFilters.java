package com.caseflow.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Optional scope for similar-case retrieval, enforced on the vector search. Each null/empty
 * list means "no constraint". The caller (caseflow-be) passes the requesting agent's scope here
 * so no ticket outside it is ever returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarCasesFilters {
    /** Only tickets of these customers. */
    private List<String> customerIds;
    /** Only tickets of these groups. */
    private List<String> groupIds;
    /** Only tickets in these statuses, e.g. RESOLVED, CLOSED. */
    private List<String> statuses;
    /** Leave these tickets out — typically the ticket being worked on. */
    private List<String> excludeSourceIds;
}
