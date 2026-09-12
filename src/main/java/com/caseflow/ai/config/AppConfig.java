package com.caseflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration for all caseflow.ai.* properties.
 *
 * <p>Required properties:
 * <ul>
 *   <li>DB_URL / DB_USERNAME / DB_PASSWORD — PostgreSQL for ingestion job tracking</li>
 *   <li>OLLAMA_BASE_URL — LLM and embedding provider</li>
 *   <li>QDRANT_HOST / QDRANT_PORT — Vector store</li>
 * </ul>
 *
 * <p>Optional properties:
 * <ul>
 *   <li>ASYNC_ENABLED=true + KAFKA_BOOTSTRAP_SERVERS — activate Kafka ingest consumers</li>
 *   <li>INTERNAL_API_KEY + caseflow.ai.auth.enabled=true — service-to-service auth</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "caseflow.ai")
@Data
public class AppConfig {

    private String defaultLocale = "en";
    private int defaultTopK = 5;
    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private int qdrantHttpPort = 6333;

    /** Must match spring.ai.ollama.chat.options.model for accurate response metadata. */
    private String modelName = "llama3.1";

    private Async async = new Async();
    private Prompt prompt = new Prompt();
    private Retrieval retrieval = new Retrieval();
    private Auth auth = new Auth();

    @Data
    public static class Async {
        /**
         * Set to true (with KAFKA_BOOTSTRAP_SERVERS configured) to activate
         * Kafka ingest consumers. Default: false.
         * When false, the service boots normally and all sync endpoints remain available.
         */
        private boolean enabled = false;

        private Topic topic = new Topic();

        @Data
        public static class Topic {
            private String ticketSync = "ticket-ai-sync-requested";
            private String policyIngest = "policy-ai-ingest-requested";
            private String templateIngest = "template-ai-ingest-requested";
        }
    }

    @Data
    public static class Prompt {
        /**
         * Increment the relevant version when a prompt changes materially.
         * Versions are included in all AI assist responses for traceability.
         */
        private String summaryVersion = "1.0";
        private String replyDraftVersion = "1.0";
        private String similarCasesVersion = "1.0";
        private String policyGuidanceVersion = "1.0";
    }

    @Data
    public static class Retrieval {
        /**
         * Minimum cosine similarity score [0.0–1.0] for a document to be returned.
         * Lower threshold = more results, potentially less relevant.
         * Increase if hallucinated context is a concern.
         */
        private double similarityThreshold = 0.6;
    }

    @Data
    public static class Auth {
        /**
         * When true, all /api/** requests must carry the X-Internal-Api-Key header.
         * Disabled by default to allow Phase 1 delivery before caseflow-be integration.
         */
        private boolean enabled = false;

        /** Set via INTERNAL_API_KEY env var. Do not hard-code in config files. */
        private String internalApiKey = "";
    }
}
