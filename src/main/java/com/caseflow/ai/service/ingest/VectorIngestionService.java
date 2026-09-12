package com.caseflow.ai.service.ingest;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.persistence.entity.IngestionJob;
import com.caseflow.ai.support.ChunkingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central engine for chunking, embedding, and vector indexing with full job tracking.
 *
 * <p>All ingest paths (REST API and Kafka consumers) converge here to ensure:
 * <ul>
 *   <li>Consistent chunking and metadata attachment</li>
 *   <li>Job lifecycle tracking in {@code ai_ingestion_job}</li>
 *   <li>No silent failures — every ingest attempt has an auditable outcome</li>
 * </ul>
 *
 * <p>Note: This service does NOT delete existing chunks for an entity before re-indexing.
 * Full delete-then-reindex support (for version replacement) is deferred to a later phase.
 * For now, re-syncing an entity will result in additional chunks alongside existing ones.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorIngestionService {

    private final VectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final IngestionJobService ingestionJobService;
    private final AiMetrics aiMetrics;

    /**
     * Ingest text content for an entity into the vector store.
     *
     * @param entityType    type of entity being indexed (TICKET, POLICY, TEMPLATE)
     * @param entityId      source identifier from the originating system
     * @param jobType       kind of operation (INGEST, SYNC, REINDEX)
     * @param text          pre-built text to chunk and embed
     * @param metadata      metadata to attach to every chunk (sourceType, title, etc.)
     * @param sourceVersion optional version string for idempotency tracking
     * @param correlationId correlation ID from the originating request or Kafka event
     * @return result including jobId, status, and chunk count
     */
    public IngestResult ingest(EntityType entityType,
                                String entityId,
                                IngestionJobType jobType,
                                String text,
                                Map<String, Object> metadata,
                                String sourceVersion,
                                String correlationId) {

        log.info("Starting vector ingest: entityType={} entityId={} jobType={} correlationId={}",
                entityType, entityId, jobType, correlationId);
        aiMetrics.recordIngestionRequested();

        IngestionJob job = ingestionJobService.createJob(entityType, entityId, jobType, sourceVersion, correlationId);
        ingestionJobService.startJob(job.getJobId());

        try {
            if (text == null || text.isBlank()) {
                String reason = "Ingest text is null or blank for entityId=" + entityId;
                log.warn(reason);
                ingestionJobService.skipJob(job.getJobId(), reason);
                aiMetrics.recordIngestionSkipped();
                return IngestResult.skipped(job.getJobId(), entityId, reason);
            }

            List<String> chunks = chunkingService.chunk(text);
            if (chunks.isEmpty()) {
                String reason = "No chunks produced from text for entityId=" + entityId;
                log.warn(reason);
                ingestionJobService.skipJob(job.getJobId(), reason);
                aiMetrics.recordIngestionSkipped();
                return IngestResult.skipped(job.getJobId(), entityId, reason);
            }

            List<Document> documents = buildDocuments(chunks, entityId, metadata);
            vectorStore.add(documents);

            ingestionJobService.completeJob(job.getJobId(), chunks.size());
            aiMetrics.recordIngestionSucceeded();

            log.info("Vector ingest succeeded: entityId={} chunks={} jobId={}", entityId, chunks.size(), job.getJobId());
            return IngestResult.succeeded(job.getJobId(), entityId, chunks.size());

        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("Vector ingest failed: entityType={} entityId={} jobId={} error={}",
                    entityType, entityId, job.getJobId(), errorMessage, e);
            ingestionJobService.failJob(job.getJobId(), "INGEST_ERROR", errorMessage);
            aiMetrics.recordIngestionFailed();
            return IngestResult.failed(job.getJobId(), entityId, errorMessage);
        }
    }

    private List<Document> buildDocuments(List<String> chunks,
                                           String entityId,
                                           Map<String, Object> baseMetadata) {
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = new HashMap<>(baseMetadata);
            meta.put("chunkIndex", i);
            meta.put("chunkTotal", chunks.size());
            docs.add(new Document(chunks.get(i), meta));
        }
        return docs;
    }

    /**
     * Result of a vector ingest operation.
     */
    public record IngestResult(
            String jobId,
            String entityId,
            String status,
            int chunksIndexed,
            String message
    ) {
        static IngestResult succeeded(String jobId, String entityId, int chunks) {
            return new IngestResult(jobId, entityId, "SUCCESS", chunks,
                    "Indexed " + chunks + " chunks for entityId=" + entityId);
        }

        static IngestResult failed(String jobId, String entityId, String errorMessage) {
            return new IngestResult(jobId, entityId, "FAILED", 0, errorMessage);
        }

        static IngestResult skipped(String jobId, String entityId, String reason) {
            return new IngestResult(jobId, entityId, "SKIPPED", 0, reason);
        }
    }
}
