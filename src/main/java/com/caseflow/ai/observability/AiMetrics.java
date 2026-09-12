package com.caseflow.ai.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters for AI service operational visibility.
 * Exposed via /actuator/prometheus for Prometheus scraping.
 *
 * <p>Key metrics:
 * <ul>
 *   <li>{@code caseflow_ai_requests_total{operation}} — requests per AI assist endpoint</li>
 *   <li>{@code caseflow_ai_retrieval_empty_total{operation}} — rate of empty vector retrievals</li>
 *   <li>{@code caseflow_ai_ingestion_total{status}} — ingest requested/succeeded/failed counts</li>
 *   <li>{@code caseflow_ai_model_errors_total} — LLM call failures</li>
 * </ul>
 */
@Component
@Slf4j
public class AiMetrics {

    private final Counter summarizeRequests;
    private final Counter replyDraftRequests;
    private final Counter similarCasesRequests;
    private final Counter policyGuidanceRequests;

    private final Counter summarizeEmptyRetrieval;
    private final Counter similarCasesEmptyRetrieval;
    private final Counter policyGuidanceEmptyRetrieval;

    private final Counter ingestionRequested;
    private final Counter ingestionSucceeded;
    private final Counter ingestionFailed;
    private final Counter ingestionSkipped;

    private final Counter modelErrors;

    public AiMetrics(MeterRegistry registry) {
        this.summarizeRequests = counter(registry, "requests", "operation", "summarize");
        this.replyDraftRequests = counter(registry, "requests", "operation", "reply_draft");
        this.similarCasesRequests = counter(registry, "requests", "operation", "similar_cases");
        this.policyGuidanceRequests = counter(registry, "requests", "operation", "policy_guidance");

        this.summarizeEmptyRetrieval = counter(registry, "retrieval_empty", "operation", "summarize");
        this.similarCasesEmptyRetrieval = counter(registry, "retrieval_empty", "operation", "similar_cases");
        this.policyGuidanceEmptyRetrieval = counter(registry, "retrieval_empty", "operation", "policy_guidance");

        this.ingestionRequested = counter(registry, "ingestion", "status", "requested");
        this.ingestionSucceeded = counter(registry, "ingestion", "status", "succeeded");
        this.ingestionFailed = counter(registry, "ingestion", "status", "failed");
        this.ingestionSkipped = counter(registry, "ingestion", "status", "skipped");

        this.modelErrors = Counter.builder("caseflow_ai_model_errors_total")
                .description("Total LLM call failures")
                .register(registry);
    }

    private Counter counter(MeterRegistry registry, String suffix, String tagKey, String tagValue) {
        return Counter.builder("caseflow_ai_" + suffix + "_total")
                .tag(tagKey, tagValue)
                .register(registry);
    }

    public void recordSummarizeRequest()      { summarizeRequests.increment(); }
    public void recordReplyDraftRequest()     { replyDraftRequests.increment(); }
    public void recordSimilarCasesRequest()   { similarCasesRequests.increment(); }
    public void recordPolicyGuidanceRequest() { policyGuidanceRequests.increment(); }

    public void recordSummarizeEmptyRetrieval()      { summarizeEmptyRetrieval.increment(); }
    public void recordSimilarCasesEmptyRetrieval()   { similarCasesEmptyRetrieval.increment(); }
    public void recordPolicyGuidanceEmptyRetrieval() { policyGuidanceEmptyRetrieval.increment(); }

    public void recordIngestionRequested() { ingestionRequested.increment(); }
    public void recordIngestionSucceeded() { ingestionSucceeded.increment(); }
    public void recordIngestionFailed()    { ingestionFailed.increment(); }
    public void recordIngestionSkipped()   { ingestionSkipped.increment(); }
    public void recordModelError()         { modelErrors.increment(); }
}
