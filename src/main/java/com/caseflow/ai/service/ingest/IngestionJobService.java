package com.caseflow.ai.service.ingest;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobStatus;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.persistence.entity.IngestionJob;
import com.caseflow.ai.persistence.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of AI ingestion/indexing jobs.
 * Provides create → start → complete/fail/skip transitions with full audit trail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionJobService {

    private final IngestionJobRepository repository;

    /**
     * Create a new job in REQUESTED status.
     */
    @Transactional
    public IngestionJob createJob(EntityType entityType,
                                   String entityId,
                                   IngestionJobType jobType,
                                   String sourceVersion,
                                   String correlationId) {
        IngestionJob job = IngestionJob.builder()
                .jobId(UUID.randomUUID().toString())
                .entityType(entityType)
                .entityId(entityId)
                .jobType(jobType)
                .sourceVersion(sourceVersion)
                .correlationId(correlationId)
                .status(IngestionJobStatus.REQUESTED)
                .retryCount(0)
                .build();
        IngestionJob saved = repository.save(job);
        log.info("Created ingestion job jobId={} entityType={} entityId={} jobType={}",
                saved.getJobId(), entityType, entityId, jobType);
        return saved;
    }

    /**
     * Transition job to PROCESSING.
     */
    @Transactional
    public IngestionJob startJob(String jobId) {
        IngestionJob job = findOrThrow(jobId);
        job.setStatus(IngestionJobStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        return repository.save(job);
    }

    /**
     * Transition job to SUCCEEDED with chunk count.
     */
    @Transactional
    public IngestionJob completeJob(String jobId, int chunksIndexed) {
        IngestionJob job = findOrThrow(jobId);
        job.setStatus(IngestionJobStatus.SUCCEEDED);
        job.setChunksIndexed(chunksIndexed);
        job.setFinishedAt(Instant.now());
        log.info("Completed ingestion job jobId={} chunksIndexed={}", jobId, chunksIndexed);
        return repository.save(job);
    }

    /**
     * Transition job to FAILED with error details.
     */
    @Transactional
    public IngestionJob failJob(String jobId, String errorCode, String errorMessage) {
        IngestionJob job = findOrThrow(jobId);
        job.setStatus(IngestionJobStatus.FAILED);
        job.setErrorCode(errorCode);
        job.setErrorMessage(errorMessage);
        job.setFinishedAt(Instant.now());
        log.warn("Failed ingestion job jobId={} errorCode={} errorMessage={}", jobId, errorCode, errorMessage);
        return repository.save(job);
    }

    /**
     * Transition job to SKIPPED (e.g., duplicate version already indexed).
     */
    @Transactional
    public IngestionJob skipJob(String jobId, String reason) {
        IngestionJob job = findOrThrow(jobId);
        job.setStatus(IngestionJobStatus.SKIPPED);
        job.setErrorMessage(reason);
        job.setFinishedAt(Instant.now());
        log.info("Skipped ingestion job jobId={} reason={}", jobId, reason);
        return repository.save(job);
    }

    /**
     * Increment retry count on a failed job (without changing status — caller transitions if needed).
     */
    @Transactional
    public IngestionJob incrementRetry(String jobId) {
        IngestionJob job = findOrThrow(jobId);
        job.setRetryCount(job.getRetryCount() + 1);
        return repository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<IngestionJob> findById(String jobId) {
        return repository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public Optional<IngestionJob> findLatestByEntity(EntityType entityType, String entityId) {
        return repository.findTopByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<IngestionJob> findAllByEntity(EntityType entityType, String entityId) {
        return repository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Optional<IngestionJob> findByCorrelationId(String correlationId) {
        return repository.findByCorrelationId(correlationId);
    }

    @Transactional(readOnly = true)
    public List<IngestionJob> findFailed() {
        return repository.findByStatus(IngestionJobStatus.FAILED);
    }

    private IngestionJob findOrThrow(String jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("IngestionJob not found: " + jobId));
    }
}
