package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.TicketSummaryRequest;
import com.caseflow.ai.api.dto.TicketSummaryResponse;
import com.caseflow.ai.domain.MessageItem;
import com.caseflow.ai.service.ai.TicketSummaryService;
import com.caseflow.ai.service.prompt.SummaryPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketSummaryServiceTest {

    @Mock
    private SummaryPromptBuilder promptBuilder;

    @Mock
    private ObjectMapper objectMapper;

    private final ChatClient chatClient = mock(ChatClient.class);

    @Test
    void summarize_returnsNonNullResponse_withTicketIdSet() throws Exception {
        String ticketId = "TKT-123";

        String jsonResponse = """
                {
                  "summary": "Test summary",
                  "customerIntent": "Get help",
                  "keyPoints": ["key1"],
                  "riskSignals": [],
                  "suggestedNextStep": "Escalate",
                  "confidence": 0.85,
                  "citations": []
                }
                """;

        TicketSummaryRequest request = TicketSummaryRequest.builder()
                .ticketStatus("OPEN")
                .priority("HIGH")
                .latestMessages(List.of(
                        MessageItem.builder()
                                .direction("INBOUND")
                                .sender("user@example.com")
                                .body("Help needed.")
                                .build()))
                .build();

        when(promptBuilder.build(any())).thenReturn("build prompt");

        // Mock fluent ChatClient call chain with deep stubs
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.PromptUserSpec promptUserSpec = mock(ChatClient.PromptUserSpec.class);

        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(any(String.class))).thenReturn(promptUserSpec);
        when(promptUserSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(jsonResponse);

        TicketSummaryResponse expected = TicketSummaryResponse.builder()
                .summary("Test summary")
                .customerIntent("Get help")
                .confidence(0.85)
                .build();
        when(objectMapper.readValue(jsonResponse, TicketSummaryResponse.class)).thenReturn(expected);

        TicketSummaryService service = new TicketSummaryService(chatClient, promptBuilder, objectMapper);
        TicketSummaryResponse response = service.summarize(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getSummary()).isEqualTo("Test summary");
    }

    @Test
    void summarize_returnsDefaultResponse_whenJsonParseFails() throws Exception {
        String ticketId = "TKT-456";
        String rawResponse = "This is not JSON";

        TicketSummaryRequest request = TicketSummaryRequest.builder()
                .ticketStatus("OPEN")
                .priority("LOW")
                .latestMessages(List.of(
                        MessageItem.builder().direction("INBOUND").sender("a@b.com").body("test").build()))
                .build();

        when(promptBuilder.build(any())).thenReturn("some prompt");

        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.PromptUserSpec promptUserSpec = mock(ChatClient.PromptUserSpec.class);

        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(any(String.class))).thenReturn(promptUserSpec);
        when(promptUserSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(rawResponse);

        when(objectMapper.readValue(rawResponse, TicketSummaryResponse.class))
                .thenThrow(new RuntimeException("parse error"));

        TicketSummaryService service = new TicketSummaryService(chatClient, promptBuilder, objectMapper);
        TicketSummaryResponse response = service.summarize(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getSummary()).isEqualTo(rawResponse);
    }
}
