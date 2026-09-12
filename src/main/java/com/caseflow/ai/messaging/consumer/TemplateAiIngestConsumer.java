package com.caseflow.ai.messaging.consumer;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.messaging.event.TemplateAiIngestRequestedEvent;
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
 * Consumes {@code template-ai-ingest-requested} events and indexes reply templates for retrieval.
 * Foundation indexing only — deeper usage (template suggestion, auto-fill) is a future phase.
 *
 * <p>Only active when {@code caseflow.ai.async.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "caseflow.ai.async.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TemplateAiIngestConsumer {

    private final VectorIngestionService vectorIngestionService;

    @KafkaListener(
            topics = "${caseflow.ai.async.topic.template-ingest}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload TemplateAiIngestRequestedEvent event,
                        @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                        @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.info("Received template-ai-ingest event: templateCode={} correlationId={} topic={} offset={}",
                event.getTemplateCode(), event.getCorrelationId(), topic, offset);

        if (event.getTemplateCode() == null || event.getTemplateCode().isBlank()) {
            log.warn("Skipping template-ai-ingest event with missing templateCode correlationId={}",
                    event.getCorrelationId());
            return;
        }

        String text = buildTemplateText(event);
        Map<String, Object> metadata = buildMetadata(event);

        VectorIngestionService.IngestResult result = vectorIngestionService.ingest(
                EntityType.TEMPLATE,
                event.getTemplateCode(),
                IngestionJobType.INGEST,
                text,
                metadata,
                event.getSourceVersion(),
                event.getCorrelationId()
        );

        log.info("Template ingest complete: templateCode={} status={} chunks={} jobId={} correlationId={}",
                event.getTemplateCode(), result.status(), result.chunksIndexed(),
                result.jobId(), event.getCorrelationId());
    }

    private String buildTemplateText(TemplateAiIngestRequestedEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getName() != null) sb.append("Name: ").append(event.getName()).append("\n\n");
        if (event.getBody() != null) sb.append("Body:\n").append(event.getBody()).append("\n");
        return sb.toString();
    }

    private Map<String, Object> buildMetadata(TemplateAiIngestRequestedEvent event) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("sourceId", event.getTemplateCode());
        meta.put("sourceType", "TEMPLATE");
        meta.put("title", event.getName() != null ? event.getName() : "");
        if (event.getLocale() != null) meta.put("locale", event.getLocale());
        if (event.getSourceVersion() != null) meta.put("version", event.getSourceVersion());
        if (event.getMetadata() != null) meta.putAll(event.getMetadata());
        return meta;
    }
}
