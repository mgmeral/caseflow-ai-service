package com.caseflow.ai.persistence.entity;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobStatus;
import com.caseflow.ai.domain.IngestionJobType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tracks the lifecycle of each AI ingestion/indexing operation.
 * Allows caseflow-be (or ops tooling) to query what was indexed, when, and whether it succeeded.
 */
@Entity
@Table(
    name = "ai_ingestion_job",
    indexes = {
        @Index(name = "idx_ai_ingest_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_ai_ingest_status", columnList = "status"),
        @Index(name = "idx_ai_ingest_correlation", columnList = "correlation_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionJob {

    /** UUID generated in Java — database-agnostic. */
    @Id
    @Column(name = "job_id", length = 36, nullable = false)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 50, nullable = false)
    private EntityType entityType;

    @Column(name = "entity_id", length = 255, nullable = false)
    private String entityId;

    /** Version identifier from the source system (e.g., updatedAt timestamp). Aids idempotency. */
    @Column(name = "source_version", length = 255)
    private String sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 50, nullable = false)
    private IngestionJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private IngestionJobStatus status;

    @Column(name = "chunks_indexed")
    private Integer chunksIndexed;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Correlation ID from the originating Kafka event or REST request for end-to-end tracing. */
    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (requestedAt == null) requestedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
