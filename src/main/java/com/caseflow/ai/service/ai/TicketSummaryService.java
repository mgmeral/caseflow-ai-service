package com.caseflow.ai.service.ai;

import com.caseflow.ai.api.dto.TicketSummaryRequest;
import com.caseflow.ai.api.dto.TicketSummaryResponse;
import com.caseflow.ai.service.prompt.SummaryPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSummaryService {

    private final ChatClient chatClient;
    private final SummaryPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public TicketSummaryResponse summarize(String ticketId, TicketSummaryRequest request) {
        log.info("Processing ticket summary for ticketId={}", ticketId);
        String userPrompt = promptBuilder.build(request);
        String rawResponse = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
        return parseResponse(ticketId, rawResponse);
    }

    private TicketSummaryResponse parseResponse(String ticketId, String raw) {
        try {
            TicketSummaryResponse parsed = objectMapper.readValue(raw, TicketSummaryResponse.class);
            parsed.setTicketId(ticketId);
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse summary JSON response for ticketId={}, using raw", ticketId);
            return TicketSummaryResponse.builder()
                    .ticketId(ticketId)
                    .summary(raw)
                    .keyPoints(Collections.emptyList())
                    .riskSignals(Collections.emptyList())
                    .citations(Collections.emptyList())
                    .confidence(0.5)
                    .build();
        }
    }
}
