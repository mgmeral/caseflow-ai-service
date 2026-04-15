package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.CaseMatch;
import com.caseflow.ai.api.dto.SimilarCasesRequest;
import com.caseflow.ai.api.dto.SimilarCasesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimilarCasesService {

    private final VectorStore vectorStore;

    public SimilarCasesResponse findSimilar(String ticketId, SimilarCasesRequest request) {
        log.info("Searching similar cases for ticketId={}", ticketId);
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getQueryText())
                .topK(topK)
                .build();
        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        List<CaseMatch> matches = docs.stream().map(this::toMatch).toList();
        return SimilarCasesResponse.builder().ticketId(ticketId).matches(matches).build();
    }

    private CaseMatch toMatch(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String text = doc.getText();
        return CaseMatch.builder()
                .sourceId((String) meta.getOrDefault("sourceId", ""))
                .sourceType((String) meta.getOrDefault("sourceType", ""))
                .title((String) meta.getOrDefault("title", ""))
                .snippet(text != null ? text.substring(0, Math.min(300, text.length())) : "")
                .score(0.0)
                .metadata(meta)
                .build();
    }
}
