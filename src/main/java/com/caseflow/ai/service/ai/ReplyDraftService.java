package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.ReplyDraftRequest;
import com.caseflow.ai.api.dto.ReplyDraftResponse;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.domain.MessageItem;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.prompt.ReplyDraftPromptBuilder;
import com.caseflow.ai.service.rag.RetrievalFilter;
import com.caseflow.ai.service.rag.RetrievalService;
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
public class ReplyDraftService {

    /** Policy chunks pulled in when the caller supplies no policySnippets. */
    private static final int AUTO_POLICY_TOP_K = 3;
    private static final int POLICY_SNIPPET_MAX_CHARS = 600;

    private final ChatClient chatClient;
    private final ReplyDraftPromptBuilder promptBuilder;
    private final RetrievalService retrievalService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final AiMetrics aiMetrics;

    public ReplyDraftResponse draftReply(String ticketId, ReplyDraftRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.info("ReplyDraft correlationId={} ticketId={}", correlationId, ticketId);
        aiMetrics.recordReplyDraftRequest();

        if (request.getPolicySnippets() == null || request.getPolicySnippets().isEmpty()) {
            request.setPolicySnippets(retrievePolicySnippets(request, correlationId));
        }

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
        // Raw model output contains customer content — debug level only
        log.debug("ReplyDraft raw response={}", LlmJsonSanitizer.snippet(rawResponse));
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

    /**
     * Grounds the draft in policies (GLOBAL plus the customer's own) relevant to the latest
     * customer message. Best effort: a retrieval failure only means a draft without policy
     * grounding, never a failed draft.
     */
    private List<String> retrievePolicySnippets(ReplyDraftRequest request, String correlationId) {
        String query = latestInboundText(request.getLatestMessages());
        if (query == null) return Collections.emptyList();
        try {
            return retrievalService.search(query, AUTO_POLICY_TOP_K, RetrievalFilter.policiesFor(request.getCustomerId()))
                    .documents().stream()
                    .map(d -> d.getText() == null ? "" : d.getText())
                    .filter(t -> !t.isBlank())
                    .map(t -> t.length() > POLICY_SNIPPET_MAX_CHARS ? t.substring(0, POLICY_SNIPPET_MAX_CHARS) : t)
                    .toList();
        } catch (Exception e) {
            log.warn("ReplyDraft policy retrieval failed, drafting without policy grounding correlationId={}: {}",
                    correlationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String latestInboundText(List<MessageItem> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageItem m = messages.get(i);
            if (m.getDirection() != null && m.getDirection().equalsIgnoreCase("inbound")
                    && m.getPreview() != null && !m.getPreview().isBlank()) {
                return m.getPreview();
            }
        }
        return null;
    }

    private ReplyDraftResponse parseResponse(String ticketId, String raw) {
        try {
            ReplyDraftResponse parsed = objectMapper.readValue(raw, ReplyDraftResponse.class);
            parsed.setTicketId(ticketId);
            if (parsed.getSuggestedTags() == null) parsed.setSuggestedTags(Collections.emptyList());
            return parsed;
        } catch (Exception e) {
            aiMetrics.recordModelOutputNotJson("reply_draft");
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
