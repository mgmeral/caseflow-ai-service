package com.caseflow.ai.service.ingest;

import com.caseflow.ai.api.dto.DocumentIngestRequest;
import com.caseflow.ai.api.dto.IngestResponse;
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
public class DocumentIngestService {

    private final VectorStore vectorStore;
    private final ChunkingService chunkingService;

    public IngestResponse ingest(DocumentIngestRequest request) {
        log.info("Ingesting document sourceId={} sourceType={}", request.getSourceId(), request.getSourceType());
        List<String> chunks = chunkingService.chunk(request.getText());
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceId", request.getSourceId());
            metadata.put("sourceType", request.getSourceType());
            metadata.put("title", request.getTitle());
            metadata.put("chunkIndex", i);
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
                .message("Indexed " + chunks.size() + " chunks for sourceId=" + request.getSourceId())
                .build();
    }
}
