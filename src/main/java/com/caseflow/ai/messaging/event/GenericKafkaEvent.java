package com.caseflow.ai.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fallback deserialization type for Kafka messages whose concrete type cannot be determined.
 * Used as the ErrorHandlingDeserializer's default type to prevent startup failures
 * when message type headers are absent.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenericKafkaEvent {
    private String correlationId;
}
