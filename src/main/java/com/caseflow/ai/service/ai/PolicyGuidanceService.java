package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.PolicyGuidanceRequest;
import com.caseflow.ai.api.dto.PolicyGuidanceResponse;
import com.caseflow.ai.api.dto.PolicyReference;
import com.caseflow.ai.service.prompt.PolicyGuidancePromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyGuidanceService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PolicyGuidancePromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public PolicyGuidanceResponse getGuidance(String ticketId, PolicyGuidanceRequest request) {
        log.info("Processing policy guidance for ticketId={}", ticketId);
        int topK = request.getTopK() != null ? request.getTopK() : 5;

        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getQuery())
                .topK(topK)
                .build();
        List<Document> docs = vectorStore.similaritySearch(searchRequest);

        List<PolicyReference> policyReferences = docs.stream().map(this::toPolicyReference).toList();
        List<String> snippets = docs.stream()
                .map(d -> d.getText() != null ? d.getText() : "")
                .toList();

        String userPrompt = promptBuilder.build(request, snippets);
        String rawResponse = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();

        return parseResponse(ticketId, rawResponse, policyReferences);
    }

    private PolicyGuidanceResponse parseResponse(String ticketId, String raw, List<PolicyReference> refs) {
        try {
            PolicyGuidanceResponse parsed = objectMapper.readValue(raw, PolicyGuidanceResponse.class);
            parsed.setTicketId(ticketId);
            parsed.setPolicyReferences(refs);
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse policy guidance JSON for ticketId={}", ticketId);
            return PolicyGuidanceResponse.builder()
                    .ticketId(ticketId)
                    .answer(raw)
                    .policyReferences(refs)
                    .recommendedActions(Collections.emptyList())
                    .warnings(Collections.singletonList("Response could not be parsed as structured JSON"))
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
                .score(0.0)
                .build();
    }
}
