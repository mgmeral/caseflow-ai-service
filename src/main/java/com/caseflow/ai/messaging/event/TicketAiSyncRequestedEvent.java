package com.caseflow.ai.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Kafka event consumed from topic {@code ticket-ai-sync-requested}.
 * Published by caseflow-be when a resolved/knowledge-worthy ticket should be indexed for AI retrieval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAiSyncRequestedEvent {

    /** Correlation ID for end-to-end tracing (passed back in outcome logs). */
    private String correlationId;

    /** Ticket identifier in caseflow-be. */
    private String ticketId;

    private String subject;
    private String body;

    /** Optional summary of the resolution for richer indexing. */
    private String resolutionSummary;

    private String customerName;
    private String status;
    private List<String> tags;

    /**
     * Version string from the source system (e.g., ticket updatedAt timestamp).
     * Used for idempotency — if this version is already indexed, the job can be skipped.
     */
    private String sourceVersion;

    /** Additional metadata to attach to indexed chunks (e.g., groupId, locale). */
    private Map<String, Object> metadata;
}
