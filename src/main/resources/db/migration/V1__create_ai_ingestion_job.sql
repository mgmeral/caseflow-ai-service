-- AI ingestion job tracking table
-- Tracks lifecycle of every index/sync operation so the system can answer:
--   what was requested, what is processing, what succeeded, what failed, what should retry.

CREATE TABLE ai_ingestion_job (
    job_id          VARCHAR(36)     NOT NULL PRIMARY KEY,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       VARCHAR(255)    NOT NULL,
    source_version  VARCHAR(255),
    job_type        VARCHAR(50)     NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    chunks_indexed  INTEGER,
    requested_at    TIMESTAMP       NOT NULL,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    error_code      VARCHAR(100),
    error_message   TEXT,
    correlation_id  VARCHAR(255),
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL
);

CREATE INDEX idx_ai_ingest_entity      ON ai_ingestion_job (entity_type, entity_id);
CREATE INDEX idx_ai_ingest_status      ON ai_ingestion_job (status);
CREATE INDEX idx_ai_ingest_correlation ON ai_ingestion_job (correlation_id);
