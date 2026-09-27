package com.caseflow.ai.service.ingest;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.persistence.entity.IngestionJob;
import com.caseflow.ai.service.rag.RetrievalFilter;
import com.caseflow.ai.service.rag.VectorCollectionManager;
import com.caseflow.ai.support.ChunkingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * <p>Re-ingesting a source replaces it: its existing chunks are deleted first (by
 * {@code sourceType} + {@code sourceId}), and chunk point IDs are derived from
 * {@code sourceType:sourceId:chunkIndex}, so two concurrent ingests of the same source overwrite
 * each other instead of duplicating. Non-ticket sources without a {@code customerId} are
 * stored as {@link RetrievalFilter#GLOBAL} so customer-scoped policy retrieval still finds them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorIngestionService {

    private final VectorStore vectorStore;
    private final VectorCollectionManager collectionManager;
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

            Map<String, Object> baseMetadata = new HashMap<>(metadata);
            // Spring AI documents reject null metadata values; optional fields (e.g. a ticket
            // without a customer) are simply absent instead.
            baseMetadata.values().removeIf(java.util.Objects::isNull);
            String sourceType = String.valueOf(baseMetadata.getOrDefault("sourceType", entityType.name()));
            baseMetadata.put("sourceType", sourceType);
            baseMetadata.put("sourceId", entityId);
            if (!"TICKET".equals(sourceType)) {
                baseMetadata.putIfAbsent("customerId", RetrievalFilter.GLOBAL);
            }

            collectionManager.ensureCollection();
            deleteChunks(sourceType, entityId);
            vectorStore.add(buildDocuments(chunks, sourceType, entityId, baseMetadata));

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

    /**
     * Removes every chunk of a source from the vector store (e.g. a reopened ticket or a
     * deleted policy). Removing a source that was never indexed is a no-op.
     */
    public void deleteSource(String sourceType, String sourceId) {
        collectionManager.ensureCollection();
        deleteChunks(sourceType.toUpperCase(), sourceId);
        log.info("Vector source deleted: sourceType={} sourceId={}", sourceType, sourceId);
    }

    private void deleteChunks(String sourceType, String sourceId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        vectorStore.delete(b.and(b.eq("sourceType", sourceType), b.eq("sourceId", sourceId)).build());
    }

    private List<Document> buildDocuments(List<String> chunks,
                                           String sourceType,
                                           String sourceId,
                                           Map<String, Object> baseMetadata) {
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = new HashMap<>(baseMetadata);
            meta.put("chunkIndex", i);
            meta.put("chunkTotal", chunks.size());
            String id = UUID.nameUUIDFromBytes((sourceType + ":" + sourceId + ":" + i)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            docs.add(Document.builder().id(id).text(chunks.get(i)).metadata(meta).build());
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
