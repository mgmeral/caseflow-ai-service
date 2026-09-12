package com.caseflow.ai.service.rag;

import com.caseflow.ai.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Central retrieval service for all vector store queries.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Apply similarity threshold consistently across all retrieval paths</li>
 *   <li>Filter by {@code sourceType} metadata (POLICY, TICKET, TEMPLATE, etc.)</li>
 *   <li>Return honest empty-retrieval results with structured warnings</li>
 *   <li>Log retrieval diagnostics for observability</li>
 * </ul>
 *
 * <p>Tenant isolation: {@code customerId} and {@code groupId} filter fields are structurally
 * supported and passed through metadata. Full enforcement requires Qdrant payload indexing on
 * those fields and is deferred to the next hardening phase.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalService {

    private final VectorStore vectorStore;
    private final AppConfig appConfig;

    /**
     * Search with optional source-type filter.
     *
     * @param query          the query text for semantic search
     * @param topK           maximum number of results to return
     * @param sourceTypeFilter  if non-null, only documents with this sourceType are returned
     *                          (e.g. "POLICY", "TICKET", "TEMPLATE")
     * @return structured result with documents and a warning if retrieval is empty
     */
    public RetrievalResult search(String query, int topK, String sourceTypeFilter) {
        double threshold = appConfig.getRetrieval().getSimilarityThreshold();

        // When filtering by type, fetch more candidates to compensate for post-filtering.
        int fetchTopK = (sourceTypeFilter != null) ? Math.max(topK * 4, 20) : topK;

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(fetchTopK)
                .similarityThreshold(threshold);

        // Apply server-side filter expression when supported by the vector store.
        // Qdrant honors this natively. Post-filtering below ensures correctness as a fallback.
        if (sourceTypeFilter != null && !sourceTypeFilter.isBlank()) {
            try {
                builder.filterExpression("sourceType == '" + sourceTypeFilter.toUpperCase() + "'");
            } catch (Exception e) {
                log.warn("filterExpression not supported for this vector store — relying on post-filter only");
            }
        }

        List<Document> raw;
        try {
            raw = vectorStore.similaritySearch(builder.build());
        } catch (Exception e) {
            log.warn("Vector store search failed with filter={} — retrying without filter expression. Error: {}",
                    sourceTypeFilter, e.getMessage());
            // Fallback: wider fetch without filter expression; post-filter below
            raw = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(fetchTopK)
                    .similarityThreshold(threshold)
                    .build());
        }

        // Post-filter by sourceType for correctness (handles stores that ignore filterExpression)
        List<Document> docs = raw;
        if (sourceTypeFilter != null && !sourceTypeFilter.isBlank()) {
            String upper = sourceTypeFilter.toUpperCase();
            docs = raw.stream()
                    .filter(d -> upper.equalsIgnoreCase(
                            (String) d.getMetadata().getOrDefault("sourceType", "")))
                    .limit(topK)
                    .toList();
        }

        boolean empty = docs.isEmpty();
        String warning = empty ? buildEmptyWarning(sourceTypeFilter) : null;

        log.info("Retrieval: query_len={} sourceType={} threshold={} fetched={} after_filter={}",
                query.length(), sourceTypeFilter, threshold, raw.size(), docs.size());

        return new RetrievalResult(docs, empty, warning);
    }

    /**
     * Convenience overload for unfiltered search.
     */
    public RetrievalResult search(String query, int topK) {
        return search(query, topK, null);
    }

    private String buildEmptyWarning(String sourceTypeFilter) {
        if (sourceTypeFilter != null && !sourceTypeFilter.isBlank()) {
            String type = sourceTypeFilter.toLowerCase();
            return "No indexed " + type + " documents matched this query. "
                    + "Ingest " + type + " documents via the ingest API or Kafka consumers before using this feature.";
        }
        return "No relevant documents found for this query. The vector store may be empty.";
    }

    /**
     * Structured result from a vector retrieval call.
     *
     * @param documents   retrieved documents (may be empty — always check {@code isEmpty})
     * @param isEmpty     true when no results were found above the similarity threshold
     * @param warning     human-readable warning when empty; null when results are present
     */
    public record RetrievalResult(
            List<Document> documents,
            boolean isEmpty,
            String warning
    ) {
        public static RetrievalResult empty(String warning) {
            return new RetrievalResult(Collections.emptyList(), true, warning);
        }
    }
}
