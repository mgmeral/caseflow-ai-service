package com.caseflow.ai.service;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobStatus;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.persistence.entity.IngestionJob;
import com.caseflow.ai.persistence.repository.IngestionJobRepository;
import com.caseflow.ai.service.ingest.IngestionJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionJobServiceTest {

    @Mock private IngestionJobRepository repository;
    @InjectMocks private IngestionJobService service;

    @Test
    void createJob_persistsJobInRequestedStatus() {
        ArgumentCaptor<IngestionJob> captor = ArgumentCaptor.forClass(IngestionJob.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        IngestionJob job = service.createJob(EntityType.POLICY, "POL-1", IngestionJobType.INGEST,
                "v1", "corr-123");

        assertThat(captor.getValue().getStatus()).isEqualTo(IngestionJobStatus.REQUESTED);
        assertThat(captor.getValue().getEntityType()).isEqualTo(EntityType.POLICY);
        assertThat(captor.getValue().getEntityId()).isEqualTo("POL-1");
        assertThat(captor.getValue().getJobId()).isNotBlank();
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("corr-123");
    }

    @Test
    void startJob_updatesStatusToProcessing() {
        IngestionJob existing = buildJob("JOB-1", IngestionJobStatus.REQUESTED);
        when(repository.findById("JOB-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestionJob result = service.startJob("JOB-1");

        assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.PROCESSING);
        assertThat(result.getStartedAt()).isNotNull();
    }

    @Test
    void completeJob_updatesStatusAndChunkCount() {
        IngestionJob existing = buildJob("JOB-2", IngestionJobStatus.PROCESSING);
        when(repository.findById("JOB-2")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestionJob result = service.completeJob("JOB-2", 12);

        assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.SUCCEEDED);
        assertThat(result.getChunksIndexed()).isEqualTo(12);
        assertThat(result.getFinishedAt()).isNotNull();
    }

    @Test
    void failJob_updatesStatusAndErrorDetails() {
        IngestionJob existing = buildJob("JOB-3", IngestionJobStatus.PROCESSING);
        when(repository.findById("JOB-3")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestionJob result = service.failJob("JOB-3", "VECTOR_ERROR", "Connection refused");

        assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
        assertThat(result.getErrorCode()).isEqualTo("VECTOR_ERROR");
        assertThat(result.getErrorMessage()).isEqualTo("Connection refused");
        assertThat(result.getFinishedAt()).isNotNull();
    }

    @Test
    void skipJob_updatesStatusToSkipped() {
        IngestionJob existing = buildJob("JOB-4", IngestionJobStatus.REQUESTED);
        when(repository.findById("JOB-4")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestionJob result = service.skipJob("JOB-4", "Duplicate version already indexed");

        assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.SKIPPED);
        assertThat(result.getErrorMessage()).contains("Duplicate");
    }

    @Test
    void findOrThrow_throwsIllegalArgument_whenJobNotFound() {
        when(repository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startJob("MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MISSING");
    }

    @Test
    void incrementRetry_incrementsRetryCount() {
        IngestionJob existing = buildJob("JOB-5", IngestionJobStatus.FAILED);
        existing.setRetryCount(2);
        when(repository.findById("JOB-5")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestionJob result = service.incrementRetry("JOB-5");

        assertThat(result.getRetryCount()).isEqualTo(3);
    }

    private IngestionJob buildJob(String jobId, IngestionJobStatus status) {
        return IngestionJob.builder()
                .jobId(jobId)
                .entityType(EntityType.TICKET)
                .entityId("TKT-1")
                .jobType(IngestionJobType.INGEST)
                .status(status)
                .retryCount(0)
                .requestedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
