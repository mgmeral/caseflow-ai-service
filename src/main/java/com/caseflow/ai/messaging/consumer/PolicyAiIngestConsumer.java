package com.caseflow.ai.messaging.consumer;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.messaging.event.PolicyAiIngestRequestedEvent;
import com.caseflow.ai.service.ingest.VectorIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumes {@code policy-ai-ingest-requested} events and indexes policy documents for RAG retrieval.
 *
 * <p>Only active when {@code caseflow.ai.async.enabled=true}.
 * Policy chunks are tagged with sourceType=POLICY so the RetrievalService can filter them.
 */
@Component
@ConditionalOnProperty(name = "caseflow.ai.async.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PolicyAiIngestConsumer {

    private final VectorIngestionService vectorIngestionService;

    @KafkaListener(
            topics = "${caseflow.ai.async.topic.policy-ingest}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload PolicyAiIngestRequestedEvent event,
                        @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                        @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.info("Received policy-ai-ingest event: policyId={} correlationId={} topic={} offset={}",
                event.getPolicyId(), event.getCorrelationId(), topic, offset);

        if (event.getPolicyId() == null || event.getPolicyId().isBlank()) {
            log.warn("Skipping policy-ai-ingest event with missing policyId correlationId={}", event.getCorrelationId());
            return;
        }

        Map<String, Object> metadata = buildMetadata(event);

        VectorIngestionService.IngestResult result = vectorIngestionService.ingest(
                EntityType.POLICY,
                event.getPolicyId(),
                IngestionJobType.INGEST,
                event.getContent(),
                metadata,
                event.getSourceVersion(),
                event.getCorrelationId()
        );

        log.info("Policy ingest complete: policyId={} status={} chunks={} jobId={} correlationId={}",
                event.getPolicyId(), result.status(), result.chunksIndexed(),
                result.jobId(), event.getCorrelationId());
    }

    private Map<String, Object> buildMetadata(PolicyAiIngestRequestedEvent event) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("sourceId", event.getPolicyId());
        meta.put("sourceType", "POLICY");
        meta.put("title", event.getTitle() != null ? event.getTitle() : "");
        if (event.getLocale() != null) meta.put("locale", event.getLocale());
        if (event.getSourceVersion() != null) meta.put("version", event.getSourceVersion());
        if (event.getMetadata() != null) meta.putAll(event.getMetadata());
        return meta;
    }
}
