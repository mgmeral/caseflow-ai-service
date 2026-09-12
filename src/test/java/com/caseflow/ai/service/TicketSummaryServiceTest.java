package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.TicketSummaryRequest;
import com.caseflow.ai.api.dto.TicketSummaryResponse;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.domain.MessageItem;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.ai.TicketSummaryService;
import com.caseflow.ai.service.prompt.SummaryPromptBuilder;
import com.caseflow.ai.support.LlmJsonSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSummaryServiceTest {

    @Mock private SummaryPromptBuilder promptBuilder;
    @Mock private ObjectMapper objectMapper;
    @Mock private ChatClient chatClient;
    @Mock private AppConfig appConfig;
    @Mock private AiMetrics aiMetrics;

    private TicketSummaryService service;

    @BeforeEach
    void setUp() {
        AppConfig.Prompt promptConfig = new AppConfig.Prompt();
        when(appConfig.getModelName()).thenReturn("test-model");
        when(appConfig.getPrompt()).thenReturn(promptConfig);
        service = new TicketSummaryService(chatClient, promptBuilder, objectMapper, appConfig, aiMetrics);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void summarize_returnsContractFields_withMetadataSetServerSide() throws Exception {
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
                                .from("user@example.com")
                                .preview("Help needed.")
                                .build()))
                .build();

        when(promptBuilder.build(any())).thenReturn("built prompt");
        wireChatClient(jsonResponse);

        TicketSummaryResponse expected = TicketSummaryResponse.builder()
                .summary("Test summary")
                .customerIntent("Get help")
                .confidence(0.85)
                .build();
        when(objectMapper.readValue(LlmJsonSanitizer.sanitize(jsonResponse), TicketSummaryResponse.class)).thenReturn(expected);

        TicketSummaryResponse response = service.summarize(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getSummary()).isEqualTo("Test summary");
        assertThat(response.getCorrelationId()).isNotBlank();
        assertThat(response.getModel()).isEqualTo("test-model");
        assertThat(response.getPromptVersion()).isEqualTo("1.0");
        assertThat(response.getGeneratedAt()).isNotBlank();
        assertThat(response.getLatencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        verify(aiMetrics).recordSummarizeRequest();
    }

    @Test
    void summarize_generatedAt_isAlwaysSetServerSide() throws Exception {
        String ticketId = "TKT-GAT";
        String jsonResponse = "{\"summary\":\"s\",\"generatedAt\":\"SHOULD_BE_OVERWRITTEN\"}";

        when(promptBuilder.build(any())).thenReturn("p");
        wireChatClient(jsonResponse);

        TicketSummaryResponse fromLlm = TicketSummaryResponse.builder()
                .summary("s")
                .generatedAt("SHOULD_BE_OVERWRITTEN")
                .build();
        when(objectMapper.readValue(jsonResponse, TicketSummaryResponse.class)).thenReturn(fromLlm);

        TicketSummaryResponse response = service.summarize(ticketId, buildMinimalRequest());

        assertThat(response.getGeneratedAt())
                .isNotBlank()
                .doesNotContain("SHOULD_BE_OVERWRITTEN");
    }

    @Test
    void summarize_warnings_defaultsToEmptyList_whenLlmOmitsIt() throws Exception {
        String ticketId = "TKT-WARN";
        String jsonResponse = "{\"summary\":\"ok\"}";

        when(promptBuilder.build(any())).thenReturn("p");
        wireChatClient(jsonResponse);

        // LLM response has no warnings field
        TicketSummaryResponse fromLlm = TicketSummaryResponse.builder()
                .summary("ok")
                .warnings(null)
                .build();
        when(objectMapper.readValue(jsonResponse, TicketSummaryResponse.class)).thenReturn(fromLlm);

        TicketSummaryResponse response = service.summarize(ticketId, buildMinimalRequest());

        assertThat(response.getWarnings()).isNotNull().isEmpty();
    }

    @Test
    void summarize_correlationId_isPresent() throws Exception {
        String ticketId = "TKT-CORR";
        String json = "{\"summary\":\"s\"}";
        when(promptBuilder.build(any())).thenReturn("p");
        wireChatClient(json);
        when(objectMapper.readValue(json, TicketSummaryResponse.class))
                .thenReturn(TicketSummaryResponse.builder().summary("s").build());

        TicketSummaryResponse response = service.summarize(ticketId, buildMinimalRequest());

        assertThat(response.getCorrelationId()).isNotBlank();
    }

    // ── Parse failure ─────────────────────────────────────────────────────────

    @Test
    void summarize_returnsRawContentWithWarning_whenJsonParseFails() throws Exception {
        String ticketId = "TKT-456";
        String rawResponse = "This is not JSON";

        when(promptBuilder.build(any())).thenReturn("some prompt");
        wireChatClient(rawResponse);
        when(objectMapper.readValue(rawResponse, TicketSummaryResponse.class))
                .thenThrow(new RuntimeException("parse error"));

        TicketSummaryResponse response = service.summarize(ticketId, buildMinimalRequest());

        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getSummary()).isEqualTo(rawResponse);
        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getWarnings().get(0)).contains("MODEL_OUTPUT_NOT_JSON");
        assertThat(response.getGeneratedAt()).isNotBlank();
        assertThat(response.getCorrelationId()).isNotBlank();
    }

    // ── Fenced JSON (sanitizer path) ──────────────────────────────────────────

    @Test
    void summarize_parsesFencedJsonSuccessfully() throws Exception {
        String ticketId = "TKT-FENCED";
        String innerJson = "{\"summary\":\"Fenced summary\",\"confidence\":0.8}";
        String fencedRaw = "```json\n" + innerJson + "\n```";
        String cleaned = LlmJsonSanitizer.sanitize(fencedRaw);

        when(promptBuilder.build(any())).thenReturn("p");
        wireChatClient(fencedRaw);

        TicketSummaryResponse fromLlm = TicketSummaryResponse.builder()
                .summary("Fenced summary")
                .confidence(0.8)
                .build();
        when(objectMapper.readValue(cleaned, TicketSummaryResponse.class)).thenReturn(fromLlm);

        TicketSummaryResponse response = service.summarize(ticketId, buildMinimalRequest());

        assertThat(response.getSummary()).isEqualTo("Fenced summary");
        assertThat(response.getConfidence()).isEqualTo(0.8);
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getCorrelationId()).isNotBlank();
        assertThat(response.getGeneratedAt()).isNotBlank();
    }

    @Test
    void summarize_parsesFencedJsonWithTrailingId_realWorldPattern() throws Exception {
        String ticketId = "TKT-TRAILID";
        String innerJson = "{\"summary\":\"Trail id test\",\"confidence\":0.7}";
        String fencedRaw = "```json\n" + innerJson + "\n``` id=\"839jlwm\"";
        String cleaned = LlmJsonSanitizer.sanitize(fencedRaw);

        when(promptBuilder.build(any())).thenReturn("p");
        wireChatClient(fencedRaw);

        TicketSummaryResponse fromLlm = TicketSummaryResponse.builder()
                .summary("Trail id test")
                .confidence(0.7)
                .build();
        when(objectMapper.readValue(cleaned, TicketSummaryResponse.class)).thenReturn(fromLlm);

        TicketSummaryResponse response = service.summarize(ticketId, buildMinimalRequest());

        assertThat(response.getSummary()).isEqualTo("Trail id test");
        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    void summarize_fallsBackSafely_whenFencedContentIsMalformed() throws Exception {
        String ticketId = "TKT-FENCE-BAD";
        String fencedGarbage = "```json\nnot valid json\n```";
        String cleaned = LlmJsonSanitizer.sanitize(fencedGarbage);

        when(promptBuilder.build(any())).thenReturn("p");
        wireChatClient(fencedGarbage);
        when(objectMapper.readValue(cleaned, TicketSummaryResponse.class))
                .thenThrow(new RuntimeException("parse error"));

        TicketSummaryResponse response = service.summarize(ticketId, buildMinimalRequest());

        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getWarnings().get(0)).contains("MODEL_OUTPUT_NOT_JSON");
        assertThat(response.getSummary()).isEqualTo(fencedGarbage);
        assertThat(response.getGeneratedAt()).isNotBlank();
        assertThat(response.getCorrelationId()).isNotBlank();
    }

    // ── Provider unavailable ──────────────────────────────────────────────────

    @Test
    void summarize_returnsUnavailableResponse_whenLlmThrows() {
        String ticketId = "TKT-789";

        when(promptBuilder.build(any())).thenReturn("prompt");
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(any(String.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("Connection refused"));

        TicketSummaryResponse response = service.summarize(ticketId, buildMinimalRequest());

        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getWarnings().get(0)).contains("unavailable");
        assertThat(response.getConfidence()).isEqualTo(0.0);
        assertThat(response.getGeneratedAt()).isNotBlank();
        assertThat(response.getCorrelationId()).isNotBlank();
        verify(aiMetrics).recordModelError();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void wireChatClient(String returnValue) {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(any(String.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(returnValue);
    }

    private TicketSummaryRequest buildMinimalRequest() {
        return TicketSummaryRequest.builder()
                .latestMessages(List.of(
                        MessageItem.builder()
                                .direction("INBOUND")
                                .from("a@b.com")
                                .preview("help")
                                .sentAt("2024-01-01T10:00:00Z")
                                .build()))
                .build();
    }
}
