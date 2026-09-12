package com.caseflow.ai.service;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobStatus;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.persistence.entity.IngestionJob;
import com.caseflow.ai.service.ingest.IngestionJobService;
import com.caseflow.ai.service.ingest.VectorIngestionService;
import com.caseflow.ai.support.ChunkingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorIngestionServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private ChunkingService chunkingService;
    @Mock private IngestionJobService ingestionJobService;
    @Mock private AiMetrics aiMetrics;

    @InjectMocks private VectorIngestionService service;

    private IngestionJob buildJob(String jobId) {
        return IngestionJob.builder()
                .jobId(jobId)
                .entityType(EntityType.POLICY)
                .entityId("POL-1")
                .jobType(IngestionJobType.INGEST)
                .status(IngestionJobStatus.REQUESTED)
                .retryCount(0)
                .requestedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void ingest_succeeds_whenTextIsValid() {
        IngestionJob job = buildJob("JOB-A");
        when(ingestionJobService.createJob(any(), anyString(), any(), any(), any())).thenReturn(job);
        when(ingestionJobService.startJob(anyString())).thenReturn(job);
        when(ingestionJobService.completeJob(anyString(), anyInt())).thenReturn(job);
        when(chunkingService.chunk(anyString())).thenReturn(List.of("chunk1", "chunk2"));

        VectorIngestionService.IngestResult result = service.ingest(
                EntityType.POLICY, "POL-1", IngestionJobType.INGEST,
                "Policy content here.", Map.of("sourceType", "POLICY"),
                "v1", "corr-1");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.chunksIndexed()).isEqualTo(2);
        assertThat(result.jobId()).isEqualTo("JOB-A");
        verify(vectorStore).add(any());
        verify(aiMetrics).recordIngestionSucceeded();
    }

    @Test
    void ingest_skips_whenTextIsBlank() {
        IngestionJob job = buildJob("JOB-B");
        when(ingestionJobService.createJob(any(), anyString(), any(), any(), any())).thenReturn(job);
        when(ingestionJobService.startJob(anyString())).thenReturn(job);
        when(ingestionJobService.skipJob(anyString(), anyString())).thenReturn(job);

        VectorIngestionService.IngestResult result = service.ingest(
                EntityType.POLICY, "POL-2", IngestionJobType.INGEST,
                "   ", Map.of(), null, null);

        assertThat(result.status()).isEqualTo("SKIPPED");
        verify(vectorStore, never()).add(any());
        verify(aiMetrics).recordIngestionSkipped();
    }

    @Test
    void ingest_fails_whenVectorStoreThrows() {
        IngestionJob job = buildJob("JOB-C");
        when(ingestionJobService.createJob(any(), anyString(), any(), any(), any())).thenReturn(job);
        when(ingestionJobService.startJob(anyString())).thenReturn(job);
        when(ingestionJobService.failJob(anyString(), anyString(), anyString())).thenReturn(job);
        when(chunkingService.chunk(anyString())).thenReturn(List.of("chunk1"));
        doThrow(new RuntimeException("Qdrant down")).when(vectorStore).add(any());

        VectorIngestionService.IngestResult result = service.ingest(
                EntityType.TICKET, "TKT-1", IngestionJobType.INGEST,
                "Some text", Map.of("sourceType", "TICKET"), null, null);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("Qdrant down");
        verify(aiMetrics).recordIngestionFailed();
        verify(ingestionJobService).failJob(eq("JOB-C"), eq("INGEST_ERROR"), anyString());
    }

    @Test
    void ingest_skips_whenChunkingProducesNoChunks() {
        IngestionJob job = buildJob("JOB-D");
        when(ingestionJobService.createJob(any(), anyString(), any(), any(), any())).thenReturn(job);
        when(ingestionJobService.startJob(anyString())).thenReturn(job);
        when(ingestionJobService.skipJob(anyString(), anyString())).thenReturn(job);
        when(chunkingService.chunk(anyString())).thenReturn(List.of());

        VectorIngestionService.IngestResult result = service.ingest(
                EntityType.POLICY, "POL-3", IngestionJobType.INGEST,
                "text", Map.of(), null, null);

        assertThat(result.status()).isEqualTo("SKIPPED");
        verify(vectorStore, never()).add(any());
    }
}
