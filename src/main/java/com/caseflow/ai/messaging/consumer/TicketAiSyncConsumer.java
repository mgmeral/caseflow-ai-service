package com.caseflow.ai.messaging.consumer;

import com.caseflow.ai.domain.EntityType;
import com.caseflow.ai.domain.IngestionJobType;
import com.caseflow.ai.messaging.event.TicketAiSyncRequestedEvent;
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
 * Consumes {@code ticket-ai-sync-requested} events and indexes resolved tickets for similarity search.
 *
 * <p>This bean is only created when {@code caseflow.ai.async.enabled=true}.
 * When async is disabled, this consumer does not exist and no Kafka connections are made.
 *
 * <p>Idempotency: duplicate events for the same ticketId + sourceVersion will each create
 * a separate ingestion job. Full idempotency enforcement (skip-if-already-indexed) is a
 * planned hardening item.
 */
@Component
@ConditionalOnProperty(name = "caseflow.ai.async.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TicketAiSyncConsumer {

    private final VectorIngestionService vectorIngestionService;

    @KafkaListener(
            topics = "${caseflow.ai.async.topic.ticket-sync}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload TicketAiSyncRequestedEvent event,
                        @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                        @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.info("Received ticket-ai-sync event: ticketId={} correlationId={} topic={} offset={}",
                event.getTicketId(), event.getCorrelationId(), topic, offset);

        if (event.getTicketId() == null || event.getTicketId().isBlank()) {
            log.warn("Skipping ticket-ai-sync event with missing ticketId correlationId={}", event.getCorrelationId());
            return;
        }

        String text = buildTicketText(event);
        Map<String, Object> metadata = buildMetadata(event);

        VectorIngestionService.IngestResult result = vectorIngestionService.ingest(
                EntityType.TICKET,
                event.getTicketId(),
                IngestionJobType.SYNC,
                text,
                metadata,
                event.getSourceVersion(),
                event.getCorrelationId()
        );

        log.info("Ticket sync ingest complete: ticketId={} status={} chunks={} jobId={} correlationId={}",
                event.getTicketId(), result.status(), result.chunksIndexed(),
                result.jobId(), event.getCorrelationId());
    }

    private String buildTicketText(TicketAiSyncRequestedEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getSubject() != null) sb.append("Subject: ").append(event.getSubject()).append("\n\n");
        if (event.getBody() != null) sb.append("Body:\n").append(event.getBody()).append("\n\n");
        if (event.getResolutionSummary() != null && !event.getResolutionSummary().isBlank()) {
            sb.append("Resolution:\n").append(event.getResolutionSummary()).append("\n");
        }
        return sb.toString();
    }

    private Map<String, Object> buildMetadata(TicketAiSyncRequestedEvent event) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("sourceId", event.getTicketId());
        meta.put("sourceType", "TICKET");
        meta.put("title", event.getSubject() != null ? event.getSubject() : "");
        meta.put("customerName", event.getCustomerName() != null ? event.getCustomerName() : "");
        meta.put("status", event.getStatus() != null ? event.getStatus() : "");
        if (event.getTags() != null && !event.getTags().isEmpty()) {
            meta.put("tags", String.join(",", event.getTags()));
        }
        if (event.getMetadata() != null) {
            meta.putAll(event.getMetadata());
        }
        return meta;
    }
}
