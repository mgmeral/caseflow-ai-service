package com.caseflow.ai.service.rag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.VectorParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Owns the Qdrant collection lifecycle (Spring AI's own schema init stays off because it
 * fails application startup when Qdrant or the embedding server is down).
 *
 * <ul>
 *   <li>Creates the collection if missing, sized to the embedding model's real dimension
 *       (probed from the model, so switching models cannot silently mismatch), cosine distance.</li>
 *   <li>Refuses to write into an existing collection of a different dimension — that needs a
 *       deliberate recreate and full re-ingest, never an automatic delete.</li>
 *   <li>Creates keyword payload indexes for every field retrieval filters on.</li>
 * </ul>
 *
 * <p>Tried once when the application is ready (failure only logged — the service still serves
 * summary/reply-draft), and again before every ingest until it succeeds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VectorCollectionManager {

    /** Payload fields used by {@link RetrievalFilter}; each gets a keyword index. */
    static final List<String> INDEXED_FIELDS = List.of("sourceType", "sourceId", "customerId", "groupId", "status");

    private static final long TIMEOUT_SECONDS = 30;

    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;

    // Same default as Spring AI's QdrantVectorStore, so both always address the same collection
    @Value("${spring.ai.vectorstore.qdrant.collection-name:vector_store}")
    private String collectionName;

    @Value("${caseflow.ai.vector.bootstrap-on-startup:true}")
    private boolean bootstrapOnStartup;

    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!bootstrapOnStartup) return;
        try {
            ensureCollection();
        } catch (Exception e) {
            log.warn("Vector collection bootstrap failed at startup — will retry on first ingest: {}",
                    e.getMessage());
        }
    }

    /** Idempotent; cheap once the collection has been verified. */
    public synchronized void ensureCollection() {
        if (ready) return;
        try {
            int dimension = embeddingModel.dimensions();
            boolean exists = qdrantClient.collectionExistsAsync(collectionName).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!exists) {
                qdrantClient.createCollectionAsync(collectionName, VectorParams.newBuilder()
                        .setSize(dimension)
                        .setDistance(Distance.Cosine)
                        .build()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                log.info("Created Qdrant collection '{}' (dimension={}, distance=Cosine)", collectionName, dimension);
            } else {
                long existing = existingDimension();
                if (existing != dimension) {
                    throw new IllegalStateException(("Qdrant collection '%s' has dimension %d but the embedding "
                            + "model produces %d. Recreate the collection and re-ingest all content "
                            + "(see ADR-0005).").formatted(collectionName, existing, dimension));
                }
            }
            for (String field : INDEXED_FIELDS) {
                // Creating an index that already exists is a no-op in Qdrant.
                qdrantClient.createPayloadIndexAsync(collectionName, field, PayloadSchemaType.Keyword,
                        null, true, null, null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            ready = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while preparing Qdrant collection", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not prepare Qdrant collection '" + collectionName + "': "
                    + e.getMessage(), e);
        }
    }

    private long existingDimension() throws Exception {
        CollectionInfo info = qdrantClient.getCollectionInfoAsync(collectionName).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return info.getConfig().getParams().getVectorsConfig().getParams().getSize();
    }
}
