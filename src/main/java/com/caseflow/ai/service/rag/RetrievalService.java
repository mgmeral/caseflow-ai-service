package com.caseflow.ai.service.rag;

import com.caseflow.ai.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Central retrieval service for all vector store queries.
 *
 * <ul>
 *   <li>Applies the similarity threshold consistently across all retrieval paths</li>
 *   <li>Pushes {@link RetrievalFilter} constraints (source type, customer/group scope, status,
 *       exclusions) down to Qdrant and re-checks them on the results</li>
 *   <li>Returns honest empty results with structured warnings</li>
 * </ul>
 *
 * <p>A failing vector search is not retried without the filter — with scope constraints that
 * would return documents the caller may not see. The exception propagates instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalService {

    private final VectorStore vectorStore;
    private final AppConfig appConfig;

    /**
     * @param query  the query text for semantic search
     * @param topK   maximum number of results to return
     * @param filter metadata constraints; every returned document satisfies them
     * @return structured result with documents and a warning if retrieval is empty
     */
    public RetrievalResult search(String query, int topK, RetrievalFilter filter) {
        double threshold = appConfig.getRetrieval().getSimilarityThreshold();
        RetrievalFilter f = filter != null ? filter : RetrievalFilter.ofSourceType(null);

        // Over-fetch when filtering so the post-filter still leaves topK results.
        int fetchTopK = f.isEmpty() ? topK : Math.max(topK * 4, 20);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(fetchTopK)
                .similarityThreshold(threshold);
        Filter.Expression expression = f.toExpression();
        if (expression != null) {
            builder.filterExpression(expression);
        }

        List<Document> raw = vectorStore.similaritySearch(builder.build());
        List<Document> matching = raw.stream().filter(d -> f.matches(d.getMetadata())).toList();
        if (matching.size() < raw.size()) {
            log.warn("Retrieval: vector store returned {} document(s) outside the filter — dropped [filter={}]",
                    raw.size() - matching.size(), f);
        }
        List<Document> docs = matching.stream().limit(topK).toList();

        boolean empty = docs.isEmpty();
        log.info("Retrieval: query_len={} filter={} threshold={} fetched={} after_filter={}",
                query.length(), f, threshold, raw.size(), docs.size());
        return new RetrievalResult(docs, empty, empty ? buildEmptyWarning(f.sourceType()) : null);
    }

    /** Unfiltered search. */
    public RetrievalResult search(String query, int topK) {
        return search(query, topK, null);
    }

    private String buildEmptyWarning(String sourceType) {
        if (sourceType != null && !sourceType.isBlank()) {
            String type = sourceType.toLowerCase();
            return "No indexed " + type + " documents matched this query. "
                    + "Ingest " + type + " documents via the ingest API before using this feature.";
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
