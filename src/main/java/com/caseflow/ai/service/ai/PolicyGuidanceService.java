package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.PolicyGuidanceRequest;
import com.caseflow.ai.api.dto.PolicyGuidanceResponse;
import com.caseflow.ai.api.dto.PolicyReference;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.prompt.PolicyGuidancePromptBuilder;
import com.caseflow.ai.service.rag.RetrievalService;
import com.caseflow.ai.service.rag.RetrievalService.RetrievalResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides policy guidance grounded exclusively in retrieved policy documents.
 *
 * <p><strong>Grounding rule:</strong> If no policy documents are retrieved, this service
 * returns an explicit "no relevant policy found" response rather than allowing the LLM
 * to fabricate policy guidance. Callers must inspect {@code warnings} and {@code policyReferences}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyGuidanceService {

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;
    private final PolicyGuidancePromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final AiMetrics aiMetrics;

    public PolicyGuidanceResponse getGuidance(String ticketId, PolicyGuidanceRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("PolicyGuidance requestId={} ticketId={}", requestId, ticketId);
        aiMetrics.recordPolicyGuidanceRequest();

        long start = System.currentTimeMillis();
        int topK = request.getTopK() != null ? request.getTopK() : appConfig.getDefaultTopK();

        // Retrieve only POLICY documents — no cross-type contamination
        RetrievalResult result = retrievalService.search(request.getQuery(), topK, "POLICY");
        long retrievalMs = System.currentTimeMillis() - start;

        List<String> warnings = new ArrayList<>();

        // GROUNDING RULE: If no policy docs found, do NOT call the LLM for policy guidance.
        // Return an honest no-policy-found response to prevent hallucinated policy claims.
        if (result.isEmpty()) {
            aiMetrics.recordPolicyGuidanceEmptyRetrieval();
            warnings.add(result.warning());
            log.info("PolicyGuidance: no policy documents found for ticketId={} requestId={}", ticketId, requestId);
            long latency = System.currentTimeMillis() - start;
            return PolicyGuidanceResponse.builder()
                    .requestId(requestId)
                    .ticketId(ticketId)
                    .model(appConfig.getModelName())
                    .promptVersion(appConfig.getPrompt().getPolicyGuidanceVersion())
                    .generatedAt(Instant.now().toString())
                    .latencyMs(latency)
                    .answer("No relevant policy documents were found for this query. "
                            + "Ingest policy documents via the ingest API before requesting policy guidance.")
                    .recommendedActions(Collections.emptyList())
                    .policyReferences(Collections.emptyList())
                    .confidence(0.0)
                    .warnings(warnings)
                    .build();
        }

        List<PolicyReference> policyReferences = result.documents().stream()
                .map(this::toPolicyReference)
                .toList();
        List<String> snippets = result.documents().stream()
                .map(d -> d.getText() != null ? d.getText() : "")
                .toList();

        long llmStart = System.currentTimeMillis();
        String rawResponse;
        try {
            String userPrompt = promptBuilder.build(request, snippets);
            rawResponse = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM call failed for policyGuidance requestId={} ticketId={}: {}", requestId, ticketId, e.getMessage());
            aiMetrics.recordModelError();
            long latency = System.currentTimeMillis() - start;
            return PolicyGuidanceResponse.builder()
                    .requestId(requestId)
                    .ticketId(ticketId)
                    .model(appConfig.getModelName())
                    .promptVersion(appConfig.getPrompt().getPolicyGuidanceVersion())
                    .generatedAt(Instant.now().toString())
                    .latencyMs(latency)
                    .answer("AI model unavailable — policy guidance could not be generated.")
                    .recommendedActions(Collections.emptyList())
                    .policyReferences(policyReferences)
                    .confidence(0.0)
                    .warnings(List.of("AI model unavailable — policy guidance could not be generated",
                            result.documents().size() + " policy document(s) were retrieved but not processed"))
                    .build();
        }

        long latency = System.currentTimeMillis() - start;
        PolicyGuidanceResponse response = parseResponse(ticketId, rawResponse, policyReferences);
        response.setRequestId(requestId);
        response.setModel(appConfig.getModelName());
        response.setPromptVersion(appConfig.getPrompt().getPolicyGuidanceVersion());
        response.setGeneratedAt(Instant.now().toString());
        response.setLatencyMs(latency);

        log.info("PolicyGuidance complete requestId={} ticketId={} policyDocs={} llmMs={} totalMs={}",
                requestId, ticketId, result.documents().size(),
                System.currentTimeMillis() - llmStart, latency);
        return response;
    }

    private PolicyGuidanceResponse parseResponse(String ticketId, String raw, List<PolicyReference> refs) {
        try {
            PolicyGuidanceResponse parsed = objectMapper.readValue(raw, PolicyGuidanceResponse.class);
            parsed.setTicketId(ticketId);
            parsed.setPolicyReferences(refs);
            // Ensure warnings list is mutable for potential appends
            if (parsed.getWarnings() == null) {
                parsed.setWarnings(Collections.emptyList());
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse policy guidance JSON for ticketId={} — returning raw content", ticketId);
            return PolicyGuidanceResponse.builder()
                    .ticketId(ticketId)
                    .answer(raw)
                    .policyReferences(refs)
                    .recommendedActions(Collections.emptyList())
                    .warnings(List.of("Response could not be parsed as structured JSON — raw content returned"))
                    .confidence(0.5)
                    .build();
        }
    }

    private PolicyReference toPolicyReference(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String text = doc.getText();
        return PolicyReference.builder()
                .sourceId((String) meta.getOrDefault("sourceId", ""))
                .title((String) meta.getOrDefault("title", ""))
                .snippet(text != null ? text.substring(0, Math.min(300, text.length())) : "")
                .score(doc.getScore() != null ? doc.getScore() : 0.0)
                .build();
    }
}
