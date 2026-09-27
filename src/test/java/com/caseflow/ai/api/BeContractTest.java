package com.caseflow.ai.api;

import com.caseflow.ai.api.dto.*;
import com.caseflow.ai.service.ai.*;
import com.caseflow.ai.service.ingest.DocumentIngestService;
import com.caseflow.ai.service.ingest.TicketIngestService;
import com.caseflow.ai.service.ingest.VectorIngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test: every request body {@code caseflow-be} actually sends must pass this service's
 * bean validation and bind to the fields the AI services read.
 *
 * <p>The fixtures under {@code be-contract/} are copies of
 * {@code caseflow-be/src/test/resources/ai-contract/requests/}, where BE's
 * {@code CaseflowAiClientContractTest} asserts it serializes exactly this JSON. Before AI-001 the
 * similar-cases and policy-guidance requests failed validation here (400) on every call.
 */
@WebMvcTest({TicketAiController.class, IngestController.class})
class BeContractTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TicketSummaryService ticketSummaryService;
    @MockBean private ReplyDraftService replyDraftService;
    @MockBean private SimilarCasesService similarCasesService;
    @MockBean private PolicyGuidanceService policyGuidanceService;
    @MockBean private DocumentIngestService documentIngestService;
    @MockBean private TicketIngestService ticketIngestService;
    @MockBean private VectorIngestionService vectorIngestionService;

    @Test
    void summaryRequestFromBe_isAccepted() throws Exception {
        when(ticketSummaryService.summarize(eq("1"), any())).thenReturn(new TicketSummaryResponse());

        postFixture("/api/ai/tickets/1/summary", "summary-request.json");

        ArgumentCaptor<TicketSummaryRequest> captor = ArgumentCaptor.forClass(TicketSummaryRequest.class);
        verify(ticketSummaryService).summarize(eq("1"), captor.capture());
        TicketSummaryRequest req = captor.getValue();
        assertThat(req.getTicketStatus()).isEqualTo("IN_PROGRESS");
        assertThat(req.getPriority()).isEqualTo("MEDIUM");
        assertThat(req.getLocale()).isEqualTo("en");
        assertThat(req.getLatestMessages()).singleElement().satisfies(m -> {
            assertThat(m.getDirection()).isEqualTo("inbound");
            assertThat(m.getPreview()).isEqualTo("I cannot log in since yesterday.");
            assertThat(m.getSentAt()).isEqualTo("2026-09-27T09:00:00Z");
        });
    }

    @Test
    void replyDraftRequestFromBe_isAccepted() throws Exception {
        when(replyDraftService.draftReply(eq("1"), any())).thenReturn(new ReplyDraftResponse());

        postFixture("/api/ai/tickets/1/reply-draft", "reply-draft-request.json");

        ArgumentCaptor<ReplyDraftRequest> captor = ArgumentCaptor.forClass(ReplyDraftRequest.class);
        verify(replyDraftService).draftReply(eq("1"), captor.capture());
        ReplyDraftRequest req = captor.getValue();
        assertThat(req.getTone()).isEqualTo("professional");
        assertThat(req.getReplyGoal()).isEqualTo("RESOLUTION");
        assertThat(req.getSelectedTemplateCode()).isEqualTo("CUSTOMER_REPLY");
        assertThat(req.getCustomerId()).isEqualTo("42");
        assertThat(req.getLatestMessages()).hasSize(1);
    }

    @Test
    void similarCasesRequestFromBe_isAccepted() throws Exception {
        when(similarCasesService.findSimilar(eq("1"), any())).thenReturn(new SimilarCasesResponse());

        postFixture("/api/ai/tickets/1/similar-cases", "similar-cases-request.json");

        ArgumentCaptor<SimilarCasesRequest> captor = ArgumentCaptor.forClass(SimilarCasesRequest.class);
        verify(similarCasesService).findSimilar(eq("1"), captor.capture());
        SimilarCasesRequest req = captor.getValue();
        assertThat(req.getQueryText()).startsWith("Login issue");
        assertThat(req.getTopK()).isEqualTo(30);
        assertThat(req.getFilters().getStatuses()).containsExactly("CLOSED", "RESOLVED");
        assertThat(req.getFilters().getExcludeSourceIds()).containsExactly("7d1f7e2a-3b8c-4d5e-9f10-1a2b3c4d5e6f");
        assertThat(req.getTags()).containsExactly("AUTH");
    }

    @Test
    void policyGuidanceRequestFromBe_isAccepted() throws Exception {
        when(policyGuidanceService.getGuidance(eq("1"), any())).thenReturn(new PolicyGuidanceResponse());

        postFixture("/api/ai/tickets/1/policy-guidance", "policy-guidance-request.json");

        ArgumentCaptor<PolicyGuidanceRequest> captor = ArgumentCaptor.forClass(PolicyGuidanceRequest.class);
        verify(policyGuidanceService).getGuidance(eq("1"), captor.capture());
        PolicyGuidanceRequest req = captor.getValue();
        assertThat(req.getQuery()).isEqualTo("What is the refund policy?");
        assertThat(req.getTicketStatus()).isEqualTo("IN_PROGRESS");
        assertThat(req.getPriority()).isEqualTo("MEDIUM");
        assertThat(req.getTopK()).isEqualTo(5);
        assertThat(req.getCustomerId()).isEqualTo("42");
    }

    @Test
    void ticketIngestRequestFromBe_isAccepted() throws Exception {
        when(ticketIngestService.ingest(any())).thenReturn(new IngestResponse());

        postFixture("/api/ai/ingest/tickets", "ticket-ingest-request.json");

        ArgumentCaptor<TicketIngestRequest> captor = ArgumentCaptor.forClass(TicketIngestRequest.class);
        verify(ticketIngestService).ingest(captor.capture());
        TicketIngestRequest req = captor.getValue();
        assertThat(req.getSourceId()).isEqualTo("7d1f7e2a-3b8c-4d5e-9f10-1a2b3c4d5e6f");
        assertThat(req.getCustomerId()).isEqualTo("42");
        assertThat(req.getGroupId()).isEqualTo("7");
        assertThat(req.getStatus()).isEqualTo("RESOLVED");
        assertThat(req.getResolutionSummary()).startsWith("Cleared");
    }

    private void postFixture(String path, String fixture) throws Exception {
        String body = new ClassPathResource("be-contract/" + fixture).getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-1")
                        .header("X-Source", "caseflow-be")
                        .content(body))
                .andExpect(status().isOk());
    }
}
