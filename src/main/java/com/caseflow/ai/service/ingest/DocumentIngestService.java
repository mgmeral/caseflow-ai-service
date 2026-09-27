package com.caseflow.ai.service.ingest;

import com.caseflow.ai.api.dto.DocumentIngestRequest;
import com.caseflow.ai.api.dto.IngestResponse;
import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.service.rag.RetrievalFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestService {

    private final VectorIngestionService vectorIngestionService;

    public IngestResponse ingest(DocumentIngestRequest request) {
        log.info("Ingesting document sourceId={} sourceType={}", request.getSourceId(), request.getSourceType());

        EntityType entityType = resolveEntityType(request.getSourceType());
        Map<String, Object> metadata = buildMetadata(request);
        String correlationId = UUID.randomUUID().toString();

        VectorIngestionService.IngestResult result = vectorIngestionService.ingest(
                entityType,
                request.getSourceId(),
                IngestionJobType.INGEST,
                request.getText(),
                metadata,
                null,
                correlationId
        );

        return IngestResponse.builder()
                .jobId(result.jobId())
                .sourceId(result.entityId())
                .chunksIndexed(result.chunksIndexed())
                .status(result.status())
                .message(result.message())
                .build();
    }

    private EntityType resolveEntityType(String sourceType) {
        if (sourceType == null) return EntityType.POLICY;
        return switch (sourceType.toUpperCase()) {
            case "TICKET" -> EntityType.TICKET;
            case "TEMPLATE" -> EntityType.TEMPLATE;
            default -> EntityType.POLICY;
        };
    }

    private Map<String, Object> buildMetadata(DocumentIngestRequest request) {
        // Free-form metadata first, so it can never override the keys retrieval filters rely on.
        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        metadata.put("sourceId", request.getSourceId());
        metadata.put("sourceType", request.getSourceType() != null
                ? request.getSourceType().toUpperCase() : "POLICY");
        metadata.put("title", request.getTitle());
        metadata.put("customerId", request.getCustomerId() != null && !request.getCustomerId().isBlank()
                ? request.getCustomerId() : RetrievalFilter.GLOBAL);
        return metadata;
    }
}
