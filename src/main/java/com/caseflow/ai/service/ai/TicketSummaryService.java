package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.TicketSummaryRequest;
import com.caseflow.ai.api.dto.TicketSummaryResponse;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.prompt.SummaryPromptBuilder;
import com.caseflow.ai.support.LlmJsonSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSummaryService {

    private final ChatClient chatClient;
    private final SummaryPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final AiMetrics aiMetrics;

    public TicketSummaryResponse summarize(String ticketId, TicketSummaryRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.info("Summarize correlationId={} ticketId={}", correlationId, ticketId);
        aiMetrics.recordSummarizeRequest();

        long start = System.currentTimeMillis();
        String rawResponse;
        try {
            String userPrompt = promptBuilder.build(request);
            rawResponse = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM call failed for summarize correlationId={} ticketId={}: {}", correlationId, ticketId, e.getMessage());
            aiMetrics.recordModelError();
            long latency = System.currentTimeMillis() - start;
            return TicketSummaryResponse.builder()
                    .correlationId(correlationId)
                    .ticketId(ticketId)
                    .model(appConfig.getModelName())
                    .promptVersion(appConfig.getPrompt().getSummaryVersion())
                    .generatedAt(Instant.now().toString())
                    .latencyMs(latency)
                    .warnings(List.of("AI model unavailable — summary could not be generated"))
                    .keyPoints(Collections.emptyList())
                    .riskSignals(Collections.emptyList())
                    .citations(Collections.emptyList())
                    .confidence(0.0)
                    .build();
        }

        long latency = System.currentTimeMillis() - start;
        log.debug("Summarize raw response correlationId={} raw={}", correlationId, LlmJsonSanitizer.snippet(rawResponse));
        log.debug("Summarize raw response correlationId={} raw={}", correlationId, rawResponse);
        TicketSummaryResponse response = parseResponse(ticketId, rawResponse);

        // Metadata is always set server-side, never trusted from LLM output
        response.setCorrelationId(correlationId);
        response.setModel(appConfig.getModelName());
        response.setPromptVersion(appConfig.getPrompt().getSummaryVersion());
        response.setGeneratedAt(Instant.now().toString());
        response.setLatencyMs(latency);

        // Ensure warnings is never null
        if (response.getWarnings() == null) {
            response.setWarnings(Collections.emptyList());
        }

        log.info("Summarize complete correlationId={} ticketId={} latencyMs={}", correlationId, ticketId, latency);
        return response;
    }

    private TicketSummaryResponse parseResponse(String ticketId, String raw) {
        String cleaned = LlmJsonSanitizer.sanitize(raw);
        log.debug("Summarize cleaned response ticketId={} cleaned={}", ticketId, LlmJsonSanitizer.snippet(cleaned));
        try {
            TicketSummaryResponse parsed = objectMapper.readValue(cleaned, TicketSummaryResponse.class);
            parsed.setTicketId(ticketId);
            if (parsed.getKeyPoints() == null) parsed.setKeyPoints(Collections.emptyList());
            if (parsed.getRiskSignals() == null) parsed.setRiskSignals(Collections.emptyList());
            if (parsed.getCitations() == null) parsed.setCitations(Collections.emptyList());
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse summary JSON for ticketId={} raw={} — returning raw content with warning",
                    ticketId, LlmJsonSanitizer.snippet(raw));
            return TicketSummaryResponse.builder()
                    .ticketId(ticketId)
                    .summary(raw)
                    .keyPoints(Collections.emptyList())
                    .riskSignals(Collections.emptyList())
                    .citations(Collections.emptyList())
                    .confidence(0.5)
                    .warnings(List.of("MODEL_OUTPUT_NOT_JSON: response could not be parsed as structured JSON — raw content returned"))
                    .build();
        }
    }
}
