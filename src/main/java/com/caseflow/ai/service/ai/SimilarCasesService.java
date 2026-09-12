package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.CaseMatch;
import com.caseflow.ai.api.dto.SimilarCasesRequest;
import com.caseflow.ai.api.dto.SimilarCasesResponse;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.rag.RetrievalService;
import com.caseflow.ai.service.rag.RetrievalService.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Retrieves semantically similar resolved/known cases from the vector store.
 * Results are grounded in indexed content — this service never fabricates similar cases.
 * An empty result means no matching cases are indexed, not that no similar cases exist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimilarCasesService {

    private final RetrievalService retrievalService;
    private final AppConfig appConfig;
    private final AiMetrics aiMetrics;

    public SimilarCasesResponse findSimilar(String ticketId, SimilarCasesRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("SimilarCases requestId={} ticketId={}", requestId, ticketId);
        aiMetrics.recordSimilarCasesRequest();

        long start = System.currentTimeMillis();
        int topK = request.getTopK() != null ? request.getTopK() : appConfig.getDefaultTopK();

        // Search only indexed TICKET documents
        RetrievalResult result = retrievalService.search(request.getQueryText(), topK, "TICKET");
        long latencyMs = System.currentTimeMillis() - start;

        List<String> warnings = new ArrayList<>();
        if (result.isEmpty()) {
            aiMetrics.recordSimilarCasesEmptyRetrieval();
            warnings.add(result.warning());
            log.info("SimilarCases: no results for ticketId={} requestId={}", ticketId, requestId);
        }

        List<CaseMatch> matches = result.documents().stream()
                .map(this::toMatch)
                .toList();

        return SimilarCasesResponse.builder()
                .requestId(requestId)
                .ticketId(ticketId)
                .model(appConfig.getModelName())
                .promptVersion(appConfig.getPrompt().getSimilarCasesVersion())
                .generatedAt(Instant.now().toString())
                .latencyMs(latencyMs)
                .matches(matches)
                .warnings(warnings.isEmpty() ? Collections.emptyList() : warnings)
                .build();
    }

    private CaseMatch toMatch(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String text = doc.getText();
        return CaseMatch.builder()
                .sourceId((String) meta.getOrDefault("sourceId", ""))
                .sourceType((String) meta.getOrDefault("sourceType", ""))
                .title((String) meta.getOrDefault("title", ""))
                .snippet(text != null ? text.substring(0, Math.min(300, text.length())) : "")
                .score(doc.getScore() != null ? doc.getScore() : 0.0)
                .metadata(meta)
                .build();
    }
}
