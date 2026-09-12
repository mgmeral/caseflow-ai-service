package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.PolicyGuidanceRequest;
import com.caseflow.ai.api.dto.PolicyGuidanceResponse;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.ai.PolicyGuidanceService;
import com.caseflow.ai.service.prompt.PolicyGuidancePromptBuilder;
import com.caseflow.ai.service.rag.RetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyGuidanceServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private RetrievalService retrievalService;
    @Mock private PolicyGuidancePromptBuilder promptBuilder;
    @Mock private ObjectMapper objectMapper;
    @Mock private AppConfig appConfig;
    @Mock private AiMetrics aiMetrics;

    private PolicyGuidanceService service;

    @BeforeEach
    void setUp() {
        AppConfig.Prompt promptConfig = new AppConfig.Prompt();
        when(appConfig.getModelName()).thenReturn("test-model");
        when(appConfig.getPrompt()).thenReturn(promptConfig);
        when(appConfig.getDefaultTopK()).thenReturn(5);
        service = new PolicyGuidanceService(chatClient, retrievalService, promptBuilder, objectMapper, appConfig, aiMetrics);
    }

    @Test
    void getGuidance_returnsNoPolicyFoundResponse_whenRetrievalIsEmpty() {
        String ticketId = "TKT-001";
        PolicyGuidanceRequest request = PolicyGuidanceRequest.builder()
                .query("What is the refund policy?")
                .ticketStatus("OPEN")
                .priority("HIGH")
                .topK(5)
                .build();

        RetrievalService.RetrievalResult emptyResult = RetrievalService.RetrievalResult.empty(
                "No indexed policy documents matched this query.");
        when(retrievalService.search(eq("What is the refund policy?"), eq(5), eq("POLICY")))
                .thenReturn(emptyResult);

        PolicyGuidanceResponse response = service.getGuidance(ticketId, request);

        assertThat(response.getAnswer()).contains("No relevant policy documents were found");
        assertThat(response.getPolicyReferences()).isEmpty();
        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getConfidence()).isEqualTo(0.0);
        assertThat(response.getRequestId()).isNotBlank();
        assertThat(response.getModel()).isEqualTo("test-model");

        verify(chatClient, never()).prompt();
        verify(aiMetrics).recordPolicyGuidanceEmptyRetrieval();
    }

    @Test
    void getGuidance_callsLlmWithRetrievedSnippets_whenPolicyDocsFound() throws Exception {
        String ticketId = "TKT-002";
        PolicyGuidanceRequest request = PolicyGuidanceRequest.builder()
                .query("Refund window?")
                .ticketStatus("OPEN")
                .priority("LOW")
                .topK(3)
                .build();

        Document doc = Document.builder().text("Refund allowed within 30 days.")
                .metadata(Map.of("sourceId", "POL-001", "sourceType", "POLICY", "title", "Refund Policy"))
                .score(0.85).build();

        RetrievalService.RetrievalResult result = new RetrievalService.RetrievalResult(
                List.of(doc), false, null);
        when(retrievalService.search(eq("Refund window?"), eq(3), eq("POLICY"))).thenReturn(result);

        when(promptBuilder.build(any(), any())).thenReturn("policy prompt");

        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("{\"answer\":\"30 days\",\"recommendedActions\":[],\"confidence\":0.9,\"warnings\":[]}");

        PolicyGuidanceResponse parsed = PolicyGuidanceResponse.builder()
                .answer("30 days").recommendedActions(Collections.emptyList())
                .confidence(0.9).warnings(Collections.emptyList()).build();
        when(objectMapper.readValue(anyString(), eq(PolicyGuidanceResponse.class))).thenReturn(parsed);

        PolicyGuidanceResponse response = service.getGuidance(ticketId, request);

        assertThat(response.getAnswer()).isEqualTo("30 days");
        assertThat(response.getPolicyReferences()).hasSize(1);
        assertThat(response.getPolicyReferences().get(0).getSourceId()).isEqualTo("POL-001");
        assertThat(response.getConfidence()).isEqualTo(0.9);
        assertThat(response.getRequestId()).isNotBlank();
        verify(chatClient).prompt();
        verify(aiMetrics, never()).recordPolicyGuidanceEmptyRetrieval();
    }

    @Test
    void getGuidance_returnsUnavailableResponse_whenLlmThrows() {
        String ticketId = "TKT-003";
        PolicyGuidanceRequest request = PolicyGuidanceRequest.builder()
                .query("SLA policy?")
                .ticketStatus("OPEN")
                .priority("HIGH")
                .topK(5)
                .build();

        Document doc = Document.builder().text("SLA is 24h.")
                .metadata(Map.of("sourceId", "POL-002", "sourceType", "POLICY")).score(0.8).build();
        when(retrievalService.search(any(), anyInt(), any()))
                .thenReturn(new RetrievalService.RetrievalResult(List.of(doc), false, null));

        when(promptBuilder.build(any(), any())).thenReturn("prompt");
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("LLM timeout"));

        PolicyGuidanceResponse response = service.getGuidance(ticketId, request);

        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getConfidence()).isEqualTo(0.0);
        verify(aiMetrics).recordModelError();
    }
}
