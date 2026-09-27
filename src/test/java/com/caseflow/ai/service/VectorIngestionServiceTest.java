package com.caseflow.ai.service;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobStatus;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.persistence.entity.IngestionJob;
import com.caseflow.ai.service.ingest.IngestionJobService;
import com.caseflow.ai.service.ingest.VectorIngestionService;
import com.caseflow.ai.service.rag.RetrievalFilter;
import com.caseflow.ai.service.rag.VectorCollectionManager;
import com.caseflow.ai.support.ChunkingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

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
    @Mock private VectorCollectionManager collectionManager;
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
    void ingest_replacesExistingChunks_withDeterministicIds() {
        IngestionJob job = buildJob("JOB-R");
        when(ingestionJobService.createJob(any(), anyString(), any(), any(), any())).thenReturn(job);
        when(ingestionJobService.startJob(anyString())).thenReturn(job);
        when(ingestionJobService.completeJob(anyString(), anyInt())).thenReturn(job);
        when(chunkingService.chunk(anyString())).thenReturn(List.of("chunk1", "chunk2"));

        service.ingest(EntityType.TICKET, "t-1", IngestionJobType.INGEST, "text",
                Map.of("sourceType", "TICKET", "customerId", "c1"), null, null);
        service.ingest(EntityType.TICKET, "t-1", IngestionJobType.INGEST, "text",
                Map.of("sourceType", "TICKET", "customerId", "c1"), null, null);

        InOrder order = inOrder(collectionManager, vectorStore);
        order.verify(collectionManager).ensureCollection();
        order.verify(vectorStore).delete(any(Filter.Expression.class));
        order.verify(vectorStore).add(any());

        ArgumentCaptor<Filter.Expression> deleted = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore, times(2)).delete(deleted.capture());
        assertThat(deleted.getValue().toString()).contains("sourceType", "TICKET", "sourceId", "t-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> added = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(added.capture());
        List<String> firstIds = added.getAllValues().get(0).stream().map(Document::getId).toList();
        List<String> secondIds = added.getAllValues().get(1).stream().map(Document::getId).toList();
        assertThat(firstIds).doesNotHaveDuplicates().isEqualTo(secondIds);
    }

    @Test
    void ingest_policyWithoutCustomer_isStoredAsGlobal_butTicketIsNot() {
        IngestionJob job = buildJob("JOB-G");
        when(ingestionJobService.createJob(any(), anyString(), any(), any(), any())).thenReturn(job);
        when(ingestionJobService.startJob(anyString())).thenReturn(job);
        when(ingestionJobService.completeJob(anyString(), anyInt())).thenReturn(job);
        when(chunkingService.chunk(anyString())).thenReturn(List.of("chunk1"));

        service.ingest(EntityType.POLICY, "POL-9", IngestionJobType.INGEST, "text",
                Map.of("sourceType", "POLICY"), null, null);
        service.ingest(EntityType.TICKET, "t-9", IngestionJobType.INGEST, "text",
                Map.of("sourceType", "TICKET"), null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> added = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(added.capture());
        assertThat(added.getAllValues().get(0).get(0).getMetadata()).containsEntry("customerId", RetrievalFilter.GLOBAL);
        assertThat(added.getAllValues().get(1).get(0).getMetadata()).doesNotContainKey("customerId");
    }

    @Test
    void ingest_dropsNullMetadataValues() {
        // Found end to end: a ticket without a customer sent customerName=null and every
        // ingest failed with "metadata cannot have null values".
        IngestionJob job = buildJob("JOB-N");
        when(ingestionJobService.createJob(any(), anyString(), any(), any(), any())).thenReturn(job);
        when(ingestionJobService.startJob(anyString())).thenReturn(job);
        when(ingestionJobService.completeJob(anyString(), anyInt())).thenReturn(job);
        when(chunkingService.chunk(anyString())).thenReturn(List.of("chunk1"));
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("sourceType", "TICKET");
        meta.put("customerName", null);

        VectorIngestionService.IngestResult result = service.ingest(EntityType.TICKET, "t-n",
                IngestionJobType.INGEST, "text", meta, null, null);

        assertThat(result.status()).isEqualTo("SUCCESS");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> added = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(added.capture());
        assertThat(added.getValue().get(0).getMetadata()).doesNotContainKey("customerName");
    }

    @Test
    void deleteSource_deletesBySourceTypeAndId() {
        service.deleteSource("ticket", "t-5");

        ArgumentCaptor<Filter.Expression> deleted = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(deleted.capture());
        assertThat(deleted.getValue().toString()).contains("TICKET", "t-5");
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
