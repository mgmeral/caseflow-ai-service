package com.caseflow.ai.domain;

/**
 * What kind of operation an ingestion job performs.
 */
public enum IngestionJobType {
    /** Index a new document or ticket for the first time. */
    INGEST,
    /** Re-sync an existing indexed entity with updated content. */
    SYNC,
    /** Remove an entity's vectors from the store. */
    DELETE,
    /** Re-chunk, re-embed, and re-index an entity (e.g., after model change). */
    REINDEX
}
