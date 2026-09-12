package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.ReplyDraftRequest;
import com.caseflow.ai.api.dto.ReplyDraftResponse;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.prompt.ReplyDraftPromptBuilder;
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
public class ReplyDraftService {

    private final ChatClient chatClient;
    private final ReplyDraftPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final AiMetrics aiMetrics;

    public ReplyDraftResponse draftReply(String ticketId, ReplyDraftRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.info("ReplyDraft correlationId={} ticketId={}", correlationId, ticketId);
        aiMetrics.recordReplyDraftRequest();

        // Tone is determined by the service, not the LLM
        String appliedTone = (request.getTone() != null && !request.getTone().isBlank())
                ? request.getTone() : "PROFESSIONAL";

        long start = System.currentTimeMillis();
        String rawResponse;
        try {
            String userPrompt = promptBuilder.build(request);
            rawResponse = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM call failed for replyDraft correlationId={} ticketId={}: {}", correlationId, ticketId, e.getMessage());
            aiMetrics.recordModelError();
            long latency = System.currentTimeMillis() - start;
            return ReplyDraftResponse.builder()
                    .correlationId(correlationId)
                    .ticketId(ticketId)
                    .model(appConfig.getModelName())
                    .promptVersion(appConfig.getPrompt().getReplyDraftVersion())
                    .generatedAt(Instant.now().toString())
                    .latencyMs(latency)
                    .tone(appliedTone)
                    .warnings(List.of("AI model unavailable — reply draft could not be generated"))
                    .suggestedTags(Collections.emptyList())
                    .confidence(0.0)
                    .build();
        }

        long latency = System.currentTimeMillis() - start;
       log.info("ReplyDraft response={}", rawResponse);
        ReplyDraftResponse response = parseResponse(ticketId, rawResponse);

        // Metadata is always set server-side, never trusted from LLM output
        response.setCorrelationId(correlationId);
        response.setModel(appConfig.getModelName());
        response.setPromptVersion(appConfig.getPrompt().getReplyDraftVersion());
        response.setGeneratedAt(Instant.now().toString());
        response.setLatencyMs(latency);

        // Tone is controlled by the service, not by whatever the LLM returned
        response.setTone(appliedTone);

        // Ensure warnings is never null
        if (response.getWarnings() == null) {
            response.setWarnings(Collections.emptyList());
        }

        log.info("ReplyDraft complete correlationId={} ticketId={} latencyMs={}", correlationId, ticketId, latency);
        return response;
    }

    private ReplyDraftResponse parseResponse(String ticketId, String raw) {
        try {
            ReplyDraftResponse parsed = objectMapper.readValue(raw, ReplyDraftResponse.class);
            parsed.setTicketId(ticketId);
            if (parsed.getSuggestedTags() == null) parsed.setSuggestedTags(Collections.emptyList());
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse reply draft JSON for ticketId={} — returning raw body with warning", ticketId);
            return ReplyDraftResponse.builder()
                    .ticketId(ticketId)
                    .suggestedBody(raw)
                    .warnings(List.of("MODEL_OUTPUT_NOT_JSON: response could not be parsed as structured JSON — raw content returned"))
                    .suggestedTags(Collections.emptyList())
                    .confidence(0.5)
                    .build();
        }
    }
}
