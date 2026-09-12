package com.caseflow.ai.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Kafka event consumed from topic {@code policy-ai-ingest-requested}.
 * Published by caseflow-be when a policy document should be indexed for AI retrieval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAiIngestRequestedEvent {

    private String correlationId;

    /** Unique identifier for the policy document in caseflow-be. */
    private String policyId;

    private String title;
    private String content;

    /** Version/revision of the policy (e.g., "v3", "2024-01-15"). */
    private String sourceVersion;

    /** Locale of the policy content (e.g., "en", "de"). */
    private String locale;

    /**
     * Additional metadata: customerId scope (null = global), groupId, effectiveDate, etc.
     * Drives retrieval filtering — populate carefully.
     */
    private Map<String, Object> metadata;
}
