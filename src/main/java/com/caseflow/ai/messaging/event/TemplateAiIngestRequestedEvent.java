package com.caseflow.ai.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Kafka event consumed from topic {@code template-ai-ingest-requested}.
 * Published by caseflow-be when a reply template/macro should be indexed for AI retrieval.
 * Templates are indexed as a foundation — deeper usage (template suggestion, auto-fill) is deferred.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateAiIngestRequestedEvent {

    private String correlationId;

    /** Unique code or identifier for the template in caseflow-be. */
    private String templateCode;

    private String name;
    private String body;

    /** Version of the template content. */
    private String sourceVersion;

    private String locale;

    private Map<String, Object> metadata;
}
