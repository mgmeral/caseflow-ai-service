package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.ReplyDraftRequest;
import com.caseflow.ai.api.dto.ReplyDraftResponse;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.domain.MessageItem;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.ai.ReplyDraftService;
import com.caseflow.ai.service.rag.RetrievalFilter;
import com.caseflow.ai.service.rag.RetrievalService;
import com.caseflow.ai.service.prompt.ReplyDraftPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplyDraftServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private ReplyDraftPromptBuilder promptBuilder;
    @Mock private RetrievalService retrievalService;
    @Mock private ObjectMapper objectMapper;
    @Mock private AppConfig appConfig;
    @Mock private AiMetrics aiMetrics;

    private ReplyDraftService service;

    @BeforeEach
    void setUp() {
        AppConfig.Prompt promptConfig = new AppConfig.Prompt();
        when(appConfig.getModelName()).thenReturn("test-model");
        when(appConfig.getPrompt()).thenReturn(promptConfig);
        service = new ReplyDraftService(chatClient, promptBuilder, retrievalService, objectMapper, appConfig, aiMetrics);
    }

    // ── Policy grounding ──────────────────────────────────────────────────────

    @Test
    void draftReply_withoutPolicySnippets_retrievesCustomerScopedPoliciesForLatestInboundMessage() throws Exception {
        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .customerId("c1")
                .latestMessages(List.of(
                        MessageItem.builder().direction("inbound").preview("Can I get a refund?").build(),
                        MessageItem.builder().direction("outbound").preview("Looking into it.").build()))
                .build();
        Document policy = Document.builder().text("Refunds within 30 days.")
                .metadata(Map.of("sourceType", "POLICY", "customerId", "GLOBAL")).build();
        when(retrievalService.search(eq("Can I get a refund?"), eq(3), eq(RetrievalFilter.policiesFor("c1"))))
                .thenReturn(new RetrievalService.RetrievalResult(List.of(policy), false, null));
        when(promptBuilder.build(any())).thenReturn("draft prompt");
        wireChatClient("{\"suggestedBody\":\"ok\"}");
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class)))
                .thenReturn(ReplyDraftResponse.builder().suggestedBody("ok").build());

        service.draftReply("t-1", request);

        ArgumentCaptor<ReplyDraftRequest> built = ArgumentCaptor.forClass(ReplyDraftRequest.class);
        verify(promptBuilder).build(built.capture());
        assertThat(built.getValue().getPolicySnippets()).containsExactly("Refunds within 30 days.");
    }

    @Test
    void draftReply_policyRetrievalFailure_stillDrafts() throws Exception {
        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("inbound").preview("Help").build()))
                .build();
        when(retrievalService.search(any(), anyInt(), any())).thenThrow(new RuntimeException("qdrant down"));
        when(promptBuilder.build(any())).thenReturn("draft prompt");
        wireChatClient("{\"suggestedBody\":\"ok\"}");
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class)))
                .thenReturn(ReplyDraftResponse.builder().suggestedBody("ok").build());

        ReplyDraftResponse response = service.draftReply("t-1", request);

        assertThat(response.getSuggestedBody()).isEqualTo("ok");
    }

    @Test
    void draftReply_withCallerPolicySnippets_doesNotRetrieve() throws Exception {
        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .policySnippets(List.of("given"))
                .latestMessages(List.of(MessageItem.builder().direction("inbound").preview("Help").build()))
                .build();
        when(promptBuilder.build(any())).thenReturn("draft prompt");
        wireChatClient("{\"suggestedBody\":\"ok\"}");
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class)))
                .thenReturn(ReplyDraftResponse.builder().suggestedBody("ok").build());

        service.draftReply("t-1", request);

        verifyNoInteractions(retrievalService);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void draftReply_returnsContractFields_withMetadataSetServerSide() throws Exception {
        String ticketId = "TKT-200";
        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .ticketStatus("OPEN")
                .priority("MEDIUM")
                .tone("professional")
                .latestMessages(List.of(
                        MessageItem.builder()
                                .direction("INBOUND")
                                .from("c@c.com")
                                .preview("I need help.")
                                .sentAt("2024-01-01T10:00:00Z")
                                .build()))
                .build();

        when(promptBuilder.build(any())).thenReturn("draft prompt");
        String json = "{\"suggestedBody\":\"Thank you\",\"confidence\":0.88}";
        wireChatClient(json);

        ReplyDraftResponse parsed = ReplyDraftResponse.builder()
                .suggestedBody("Thank you")
                .confidence(0.88)
                .suggestedTags(Collections.emptyList())
                .tone("hallucinated-tone")  // LLM might return different tone — service must override
                .build();
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class))).thenReturn(parsed);

        ReplyDraftResponse response = service.draftReply(ticketId, request);

        assertThat(response.getSuggestedBody()).isEqualTo("Thank you");
        assertThat(response.getCorrelationId()).isNotBlank();
        assertThat(response.getModel()).isEqualTo("test-model");
        assertThat(response.getPromptVersion()).isEqualTo("1.0");
        assertThat(response.getGeneratedAt()).isNotBlank();
        assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(0L);
        verify(aiMetrics).recordReplyDraftRequest();
    }

    @Test
    void draftReply_tone_isFromRequest_notFromLlm() throws Exception {
        String ticketId = "TKT-TONE";
        ReplyDraftRequest request = buildMinimalRequest("empathetic");

        when(promptBuilder.build(any())).thenReturn("p");
        String json = "{\"suggestedBody\":\"body\",\"tone\":\"HALLUCINATED\"}";
        wireChatClient(json);

        ReplyDraftResponse fromLlm = ReplyDraftResponse.builder()
                .suggestedBody("body")
                .tone("HALLUCINATED")
                .build();
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class))).thenReturn(fromLlm);

        ReplyDraftResponse response = service.draftReply(ticketId, request);

        assertThat(response.getTone()).isEqualTo("empathetic");
    }

    @Test
    void draftReply_tone_defaultsToProfessional_whenRequestToneIsNull() throws Exception {
        String ticketId = "TKT-TONE-NULL";
        ReplyDraftRequest request = buildMinimalRequest(null);  // tone is null

        when(promptBuilder.build(any())).thenReturn("p");
        String json = "{\"suggestedBody\":\"body\"}";
        wireChatClient(json);
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class)))
                .thenReturn(ReplyDraftResponse.builder().suggestedBody("body").build());

        ReplyDraftResponse response = service.draftReply(ticketId, request);

        assertThat(response.getTone()).isEqualTo("PROFESSIONAL");
    }

    @Test
    void draftReply_generatedAt_isAlwaysSetServerSide() throws Exception {
        String ticketId = "TKT-GAT";
        when(promptBuilder.build(any())).thenReturn("p");
        String json = "{\"suggestedBody\":\"b\",\"generatedAt\":\"SHOULD_BE_OVERWRITTEN\"}";
        wireChatClient(json);

        ReplyDraftResponse fromLlm = ReplyDraftResponse.builder()
                .suggestedBody("b")
                .generatedAt("SHOULD_BE_OVERWRITTEN")
                .build();
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class))).thenReturn(fromLlm);

        ReplyDraftResponse response = service.draftReply(ticketId, buildMinimalRequest(null));

        assertThat(response.getGeneratedAt())
                .isNotBlank()
                .doesNotContain("SHOULD_BE_OVERWRITTEN");
    }

    @Test
    void draftReply_warnings_defaultsToEmptyList_whenLlmOmitsIt() throws Exception {
        String ticketId = "TKT-WARN";
        when(promptBuilder.build(any())).thenReturn("p");
        String json = "{\"suggestedBody\":\"ok\"}";
        wireChatClient(json);

        ReplyDraftResponse fromLlm = ReplyDraftResponse.builder()
                .suggestedBody("ok")
                .warnings(null)
                .build();
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class))).thenReturn(fromLlm);

        ReplyDraftResponse response = service.draftReply(ticketId, buildMinimalRequest(null));

        assertThat(response.getWarnings()).isNotNull().isEmpty();
    }

    @Test
    void draftReply_correlationId_isPresent() throws Exception {
        String ticketId = "TKT-CORR";
        when(promptBuilder.build(any())).thenReturn("p");
        String json = "{\"suggestedBody\":\"b\"}";
        wireChatClient(json);
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class)))
                .thenReturn(ReplyDraftResponse.builder().suggestedBody("b").build());

        ReplyDraftResponse response = service.draftReply(ticketId, buildMinimalRequest(null));

        assertThat(response.getCorrelationId()).isNotBlank();
    }

    // ── Parse failure ─────────────────────────────────────────────────────────

    @Test
    void draftReply_returnsRawBodyWithWarning_whenJsonParseFails() throws Exception {
        String ticketId = "TKT-201";
        when(promptBuilder.build(any())).thenReturn("prompt");
        wireChatClient("raw text not json");
        when(objectMapper.readValue(anyString(), eq(ReplyDraftResponse.class)))
                .thenThrow(new RuntimeException("parse error"));

        ReplyDraftResponse response = service.draftReply(ticketId, buildMinimalRequest(null));

        assertThat(response.getSuggestedBody()).isEqualTo("raw text not json");
        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getWarnings().get(0)).contains("structured JSON");
        assertThat(response.getGeneratedAt()).isNotBlank();
        assertThat(response.getCorrelationId()).isNotBlank();
    }

    // ── Provider unavailable ──────────────────────────────────────────────────

    @Test
    void draftReply_returnsUnavailableWarning_whenLlmThrows() {
        String ticketId = "TKT-202";
        when(promptBuilder.build(any())).thenReturn("prompt");
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("Model error"));

        ReplyDraftResponse response = service.draftReply(ticketId, buildMinimalRequest(null));

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
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(returnValue);
    }

    private ReplyDraftRequest buildMinimalRequest(String tone) {
        return ReplyDraftRequest.builder()
                .tone(tone)
                .latestMessages(List.of(
                        MessageItem.builder()
                                .direction("INBOUND")
                                .from("c@c.com")
                                .preview("help")
                                .sentAt("2024-01-01T10:00:00Z")
                                .build()))
                .build();
    }
}
