package com.caseflow.ai.api;

import com.caseflow.ai.api.dto.*;
import com.caseflow.ai.domain.MessageItem;
import com.caseflow.ai.service.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketAiController.class)
class TicketAiControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TicketSummaryService ticketSummaryService;
    @MockBean private ReplyDraftService replyDraftService;
    @MockBean private SimilarCasesService similarCasesService;
    @MockBean private PolicyGuidanceService policyGuidanceService;

    // ── Summary ──────────────────────────────────────────────────────────────

    @Test
    void summarize_returnsOk_withContractFields_whenValidRequest() throws Exception {
        String ticketId = "TKT-001";
        TicketSummaryResponse mockResponse = TicketSummaryResponse.builder()
                .correlationId("corr-123")
                .ticketId(ticketId)
                .model("llama3.1")
                .promptVersion("1.0")
                .generatedAt("2024-01-01T00:00:00Z")
                .latencyMs(250L)
                .summary("Customer is asking about refund.")
                .keyPoints(Collections.singletonList("Refund request"))
                .riskSignals(Collections.emptyList())
                .citations(Collections.emptyList())
                .warnings(Collections.emptyList())
                .build();

        when(ticketSummaryService.summarize(eq(ticketId), any())).thenReturn(mockResponse);

        TicketSummaryRequest request = TicketSummaryRequest.builder()
                .ticketStatus("OPEN")
                .priority("HIGH")
                .latestMessages(List.of(
                        MessageItem.builder().direction("INBOUND").from("c@c.com")
                                .preview("I need a refund.").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/summary", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.summary").value("Customer is asking about refund."))
                .andExpect(jsonPath("$.correlationId").value("corr-123"))
                .andExpect(jsonPath("$.model").value("llama3.1"))
                .andExpect(jsonPath("$.promptVersion").value("1.0"))
                .andExpect(jsonPath("$.generatedAt").value("2024-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.latencyMs").value(250))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.ticketId").value(ticketId));
    }

    @Test
    void summarize_returnsApplicationJson_whenServiceReturnsFallbackDto() throws Exception {
        String ticketId = "TKT-FALLBACK";
        TicketSummaryResponse fallback = TicketSummaryResponse.builder()
                .correlationId("corr-fb")
                .ticketId(ticketId)
                .model("llama3.1")
                .promptVersion("1.0")
                .generatedAt("2024-01-01T00:00:00Z")
                .latencyMs(100L)
                .summary("This is not JSON but raw LLM output")
                .warnings(List.of("MODEL_OUTPUT_NOT_JSON: response could not be parsed as structured JSON — raw content returned"))
                .build();

        when(ticketSummaryService.summarize(eq(ticketId), any())).thenReturn(fallback);

        TicketSummaryRequest request = TicketSummaryRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("INBOUND").from("a@b.com")
                        .preview("help").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/summary", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.summary").value("This is not JSON but raw LLM output"))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.warnings[0]").value("MODEL_OUTPUT_NOT_JSON: response could not be parsed as structured JSON — raw content returned"))
                .andExpect(jsonPath("$.generatedAt").value("2024-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.correlationId").value("corr-fb"));
    }

    @Test
    void summarize_warningsAndGeneratedAt_alwaysPresent() throws Exception {
        String ticketId = "TKT-ALWAYS";
        TicketSummaryResponse response = TicketSummaryResponse.builder()
                .correlationId("corr-always")
                .ticketId(ticketId)
                .generatedAt("2024-06-01T12:00:00Z")
                .warnings(Collections.emptyList())
                .summary("some summary")
                .build();

        when(ticketSummaryService.summarize(eq(ticketId), any())).thenReturn(response);

        TicketSummaryRequest request = TicketSummaryRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("INBOUND").from("x@y.com")
                        .preview("check").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/summary", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void summarize_acceptsTicketIdAsPathVariable() throws Exception {
        String ticketId = "TKT-PATH-99";
        TicketSummaryResponse mockResponse = TicketSummaryResponse.builder()
                .correlationId("corr-path")
                .ticketId(ticketId)
                .generatedAt("2024-01-01T00:00:00Z")
                .warnings(Collections.emptyList())
                .summary("Path variable test.")
                .build();

        when(ticketSummaryService.summarize(eq(ticketId), any())).thenReturn(mockResponse);

        TicketSummaryRequest request = TicketSummaryRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("INBOUND").from("a@b.com")
                        .preview("test").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/summary", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId));
    }

    @Test
    void summarize_returns400_whenLatestMessagesMissing() throws Exception {
        TicketSummaryRequest invalidRequest = TicketSummaryRequest.builder().build();

        mockMvc.perform(post("/api/ai/tickets/TKT-002/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summarize_acceptsNullableStatusAndPriority() throws Exception {
        String ticketId = "TKT-003";
        TicketSummaryResponse mockResponse = TicketSummaryResponse.builder()
                .correlationId("corr-003")
                .generatedAt("2024-01-01T00:00:00Z")
                .warnings(Collections.emptyList())
                .summary("Summary without status or priority.")
                .build();

        when(ticketSummaryService.summarize(eq(ticketId), any())).thenReturn(mockResponse);

        // ticketStatus and priority are intentionally omitted (nullable in contract)
        TicketSummaryRequest request = TicketSummaryRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("INBOUND").from("c@c.com")
                        .preview("help").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/summary", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ── Reply Draft ───────────────────────────────────────────────────────────

    @Test
    void replyDraft_returnsOk_withContractFields_whenValidRequest() throws Exception {
        String ticketId = "TKT-010";
        ReplyDraftResponse mockResponse = ReplyDraftResponse.builder()
                .correlationId("corr-456")
                .ticketId(ticketId)
                .model("llama3.1")
                .promptVersion("1.0")
                .generatedAt("2024-01-01T00:00:00Z")
                .suggestedBody("Thank you for contacting us.")
                .tone("professional")
                .warnings(Collections.emptyList())
                .suggestedTags(Collections.emptyList())
                .latencyMs(180L)
                .build();

        when(replyDraftService.draftReply(eq(ticketId), any())).thenReturn(mockResponse);

        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .tone("professional")
                .latestMessages(List.of(
                        MessageItem.builder().direction("INBOUND").from("c@c.com")
                                .preview("Help!").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/reply-draft", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.suggestedBody").value("Thank you for contacting us."))
                .andExpect(jsonPath("$.correlationId").value("corr-456"))
                .andExpect(jsonPath("$.tone").value("professional"))
                .andExpect(jsonPath("$.generatedAt").value("2024-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.latencyMs").value(180))
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void replyDraft_returnsApplicationJson_whenServiceReturnsFallbackDto() throws Exception {
        String ticketId = "TKT-RD-FALLBACK";
        ReplyDraftResponse fallback = ReplyDraftResponse.builder()
                .correlationId("corr-rd-fb")
                .ticketId(ticketId)
                .model("llama3.1")
                .promptVersion("1.0")
                .generatedAt("2024-01-01T00:00:00Z")
                .latencyMs(120L)
                .tone("PROFESSIONAL")
                .suggestedBody("Sure, I can help with that. Let me look into your account.")
                .warnings(List.of("MODEL_OUTPUT_NOT_JSON: response could not be parsed as structured JSON — raw content returned"))
                .build();

        when(replyDraftService.draftReply(eq(ticketId), any())).thenReturn(fallback);

        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("INBOUND").from("a@b.com")
                        .preview("help").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/reply-draft", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.suggestedBody").value("Sure, I can help with that. Let me look into your account."))
                .andExpect(jsonPath("$.tone").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.warnings[0]").value("MODEL_OUTPUT_NOT_JSON: response could not be parsed as structured JSON — raw content returned"))
                .andExpect(jsonPath("$.generatedAt").value("2024-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.correlationId").value("corr-rd-fb"));
    }

    @Test
    void replyDraft_warningsAndGeneratedAt_alwaysPresent() throws Exception {
        String ticketId = "TKT-RD-ALWAYS";
        ReplyDraftResponse response = ReplyDraftResponse.builder()
                .correlationId("corr-rd-always")
                .ticketId(ticketId)
                .generatedAt("2024-06-01T12:00:00Z")
                .warnings(Collections.emptyList())
                .suggestedBody("Here is your draft.")
                .tone("PROFESSIONAL")
                .build();

        when(replyDraftService.draftReply(eq(ticketId), any())).thenReturn(response);

        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("INBOUND").from("x@y.com")
                        .preview("check").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/reply-draft", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void replyDraft_acceptsTicketIdAsPathVariable() throws Exception {
        String ticketId = "TKT-PATH-REPLY";
        ReplyDraftResponse mockResponse = ReplyDraftResponse.builder()
                .correlationId("corr-path-reply")
                .ticketId(ticketId)
                .generatedAt("2024-01-01T00:00:00Z")
                .warnings(Collections.emptyList())
                .suggestedBody("Reply path test.")
                .tone("PROFESSIONAL")
                .build();

        when(replyDraftService.draftReply(eq(ticketId), any())).thenReturn(mockResponse);

        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("INBOUND").from("a@b.com")
                        .preview("test").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/reply-draft", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId));
    }

    @Test
    void replyDraft_returns400_whenLatestMessagesMissing() throws Exception {
        ReplyDraftRequest invalidRequest = ReplyDraftRequest.builder().build();

        mockMvc.perform(post("/api/ai/tickets/TKT-011/reply-draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replyDraft_acceptsNullableStatusAndPriority() throws Exception {
        String ticketId = "TKT-012";
        ReplyDraftResponse mockResponse = ReplyDraftResponse.builder()
                .correlationId("corr-012")
                .generatedAt("2024-01-01T00:00:00Z")
                .warnings(Collections.emptyList())
                .suggestedBody("Draft without status/priority.")
                .tone("PROFESSIONAL")
                .build();

        when(replyDraftService.draftReply(eq(ticketId), any())).thenReturn(mockResponse);

        ReplyDraftRequest request = ReplyDraftRequest.builder()
                .latestMessages(List.of(MessageItem.builder().direction("INBOUND").from("c@c.com")
                        .preview("issue").build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/reply-draft", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ── Similar Cases ─────────────────────────────────────────────────────────

    @Test
    void similarCases_returnsOk_withEmptyMatchesAndWarning_whenRetrievalEmpty() throws Exception {
        String ticketId = "TKT-020";
        SimilarCasesResponse emptyResponse = SimilarCasesResponse.builder()
                .requestId("req-789")
                .ticketId(ticketId)
                .model("llama3.1")
                .promptVersion("1.0")
                .matches(Collections.emptyList())
                .warnings(List.of("No indexed ticket documents matched this query."))
                .latencyMs(50L)
                .build();

        when(similarCasesService.findSimilar(eq(ticketId), any())).thenReturn(emptyResponse);

        SimilarCasesRequest request = SimilarCasesRequest.builder()
                .queryText("billing issue")
                .topK(5)
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/similar-cases", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isEmpty())
                .andExpect(jsonPath("$.warnings[0]").exists());
    }

    @Test
    void similarCases_returns400_whenQueryTextMissing() throws Exception {
        SimilarCasesRequest invalidRequest = SimilarCasesRequest.builder().build();

        mockMvc.perform(post("/api/ai/tickets/TKT-021/similar-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // ── Policy Guidance ───────────────────────────────────────────────────────

    @Test
    void policyGuidance_returnsOk_withNoPolicyFoundResponse_whenRetrievalEmpty() throws Exception {
        String ticketId = "TKT-030";
        PolicyGuidanceResponse noPolicy = PolicyGuidanceResponse.builder()
                .requestId("req-000")
                .ticketId(ticketId)
                .model("llama3.1")
                .promptVersion("1.0")
                .answer("No relevant policy documents were found for this query.")
                .policyReferences(Collections.emptyList())
                .warnings(List.of("No indexed policy documents matched this query."))
                .confidence(0.0)
                .latencyMs(30L)
                .build();

        when(policyGuidanceService.getGuidance(eq(ticketId), any())).thenReturn(noPolicy);

        PolicyGuidanceRequest request = PolicyGuidanceRequest.builder()
                .query("refund policy")
                .ticketStatus("OPEN")
                .priority("HIGH")
                .topK(5)
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/policy-guidance", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("No relevant policy documents were found for this query."))
                .andExpect(jsonPath("$.warnings[0]").exists())
                .andExpect(jsonPath("$.confidence").value(0.0));
    }

    @Test
    void policyGuidance_returns400_whenQueryMissing() throws Exception {
        PolicyGuidanceRequest invalidRequest = PolicyGuidanceRequest.builder()
                .ticketStatus("OPEN")
                .priority("HIGH")
                .build();

        mockMvc.perform(post("/api/ai/tickets/TKT-031/policy-guidance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
