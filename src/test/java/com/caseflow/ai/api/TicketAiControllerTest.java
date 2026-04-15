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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TicketSummaryService ticketSummaryService;

    @MockBean
    private ReplyDraftService replyDraftService;

    @MockBean
    private SimilarCasesService similarCasesService;

    @MockBean
    private PolicyGuidanceService policyGuidanceService;

    @Test
    void summarize_returnsOk_whenValidRequest() throws Exception {
        String ticketId = "TKT-001";
        TicketSummaryResponse mockResponse = TicketSummaryResponse.builder()
                .ticketId(ticketId)
                .summary("Customer is asking about refund.")
                .customerIntent("Get a refund")
                .keyPoints(Collections.singletonList("Refund request"))
                .riskSignals(Collections.emptyList())
                .suggestedNextStep("Process refund")
                .confidence(0.9)
                .citations(Collections.emptyList())
                .build();

        when(ticketSummaryService.summarize(eq(ticketId), any())).thenReturn(mockResponse);

        TicketSummaryRequest request = TicketSummaryRequest.builder()
                .ticketStatus("OPEN")
                .priority("HIGH")
                .latestMessages(List.of(
                        MessageItem.builder()
                                .direction("INBOUND")
                                .sender("customer@example.com")
                                .body("I need a refund please.")
                                .build()))
                .build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/summary", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId))
                .andExpect(jsonPath("$.summary").value("Customer is asking about refund."));
    }

    @Test
    void summarize_returns400_whenRequiredFieldsMissing() throws Exception {
        String ticketId = "TKT-002";
        // Missing required ticketStatus, priority and latestMessages
        TicketSummaryRequest invalidRequest = TicketSummaryRequest.builder().build();

        mockMvc.perform(post("/api/ai/tickets/{ticketId}/summary", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
