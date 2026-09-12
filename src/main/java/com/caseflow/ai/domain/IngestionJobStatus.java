package com.caseflow.ai.domain;

/**
 * Lifecycle status for an AI ingestion/indexing job.
 */
public enum IngestionJobStatus {
    /** Event received; processing not yet started. */
    REQUESTED,
    /** Chunking, embedding, or vector indexing is in progress. */
    PROCESSING,
    /** All chunks indexed successfully. */
    SUCCEEDED,
    /** Processing failed; see errorCode/errorMessage. */
    FAILED,
    /** Job was intentionally skipped (e.g., duplicate version already indexed). */
    SKIPPED
}
