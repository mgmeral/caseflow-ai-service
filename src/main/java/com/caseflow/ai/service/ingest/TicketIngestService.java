package com.caseflow.ai.service.ingest;

import com.caseflow.ai.api.dto.IngestResponse;
import com.caseflow.ai.api.dto.TicketIngestRequest;
import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketIngestService {

    private final VectorIngestionService vectorIngestionService;

    public IngestResponse ingest(TicketIngestRequest request) {
        log.info("Ingesting ticket sourceId={}", request.getSourceId());

        Map<String, Object> metadata = buildMetadata(request);
        String fullText = buildTicketText(request);
        String correlationId = UUID.randomUUID().toString();

        VectorIngestionService.IngestResult result = vectorIngestionService.ingest(
                EntityType.TICKET,
                request.getSourceId(),
                IngestionJobType.INGEST,
                fullText,
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

    private Map<String, Object> buildMetadata(TicketIngestRequest request) {
        // Free-form metadata first, so it can never override the keys retrieval filters rely on.
        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        metadata.put("sourceId", request.getSourceId());
        metadata.put("sourceType", "TICKET");
        metadata.put("title", request.getSubject());
        metadata.put("customerName", request.getCustomerName());
        metadata.put("status", request.getStatus());
        putIfPresent(metadata, "customerId", request.getCustomerId());
        putIfPresent(metadata, "groupId", request.getGroupId());
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            metadata.put("tags", String.join(",", request.getTags()));
        }
        return metadata;
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        } else {
            metadata.remove(key);
        }
    }

    private String buildTicketText(TicketIngestRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject: ").append(request.getSubject()).append("\n\n");
        sb.append("Body:\n").append(request.getBody()).append("\n\n");
        if (request.getResolutionSummary() != null && !request.getResolutionSummary().isBlank()) {
            sb.append("Resolution:\n").append(request.getResolutionSummary()).append("\n");
        }
        return sb.toString();
    }
}
