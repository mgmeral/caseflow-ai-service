package com.caseflow.ai.service.ingest;

import com.caseflow.ai.api.dto.IngestResponse;
import com.caseflow.ai.api.dto.TicketIngestRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketIngestService {

    private final VectorStore vectorStore;
    private final ChunkingService chunkingService;

    public IngestResponse ingest(TicketIngestRequest request) {
        log.info("Ingesting ticket sourceId={}", request.getSourceId());
        String fullText = buildTicketText(request);
        List<String> chunks = chunkingService.chunk(fullText);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceId", request.getSourceId());
            metadata.put("sourceType", "TICKET");
            metadata.put("title", request.getSubject());
            metadata.put("customerName", request.getCustomerName());
            metadata.put("status", request.getStatus());
            metadata.put("chunkIndex", i);
            if (request.getTags() != null) {
                metadata.put("tags", String.join(",", request.getTags()));
            }
            if (request.getMetadata() != null) {
                metadata.putAll(request.getMetadata());
            }
            documents.add(new Document(chunks.get(i), metadata));
        }

        vectorStore.add(documents);

        return IngestResponse.builder()
                .sourceId(request.getSourceId())
                .chunksIndexed(chunks.size())
                .status("SUCCESS")
                .message("Indexed " + chunks.size() + " chunks for ticketId=" + request.getSourceId())
                .build();
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
