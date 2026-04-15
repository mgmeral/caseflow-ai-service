package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.ReplyDraftRequest;
import com.caseflow.ai.api.dto.ReplyDraftResponse;
import com.caseflow.ai.service.prompt.ReplyDraftPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplyDraftService {

    private final ChatClient chatClient;
    private final ReplyDraftPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public ReplyDraftResponse draftReply(String ticketId, ReplyDraftRequest request) {
        log.info("Processing reply draft for ticketId={}", ticketId);
        String userPrompt = promptBuilder.build(request);
        String rawResponse = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
        return parseResponse(ticketId, rawResponse);
    }

    private ReplyDraftResponse parseResponse(String ticketId, String raw) {
        try {
            ReplyDraftResponse parsed = objectMapper.readValue(raw, ReplyDraftResponse.class);
            parsed.setTicketId(ticketId);
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse reply draft JSON for ticketId={}, using raw", ticketId);
            return ReplyDraftResponse.builder()
                    .ticketId(ticketId)
                    .suggestedBody(raw)
                    .warnings(Collections.singletonList("Response could not be parsed as structured JSON"))
                    .suggestedTags(Collections.emptyList())
                    .confidence(0.5)
                    .build();
        }
    }
}
