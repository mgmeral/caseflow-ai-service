package com.caseflow.ai.api;

import com.caseflow.ai.api.dto.*;
import com.caseflow.ai.service.ai.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/tickets/{ticketId}")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TicketAiController {

    private final TicketSummaryService ticketSummaryService;
    private final ReplyDraftService replyDraftService;
    private final SimilarCasesService similarCasesService;
    private final PolicyGuidanceService policyGuidanceService;

    @PostMapping(
            value = "/summary",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TicketSummaryResponse> summarize(
            @PathVariable String ticketId,
            @Valid @RequestBody TicketSummaryRequest request) {
        log.info("POST /api/ai/tickets/{}/summary", ticketId);
        TicketSummaryResponse dto = ticketSummaryService.summarize(ticketId, request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto);
    }

    @PostMapping(
            value = "/reply-draft",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ReplyDraftResponse> draftReply(
            @PathVariable String ticketId,
            @Valid @RequestBody ReplyDraftRequest request) {
        log.info("POST /api/ai/tickets/{}/reply-draft", ticketId);
        ReplyDraftResponse dto = replyDraftService.draftReply(ticketId, request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto);
    }

    @PostMapping("/similar-cases")
    public ResponseEntity<SimilarCasesResponse> findSimilarCases(
            @PathVariable String ticketId,
            @Valid @RequestBody SimilarCasesRequest request) {
        log.info("POST /api/ai/tickets/{}/similar-cases", ticketId);
        return ResponseEntity.ok(similarCasesService.findSimilar(ticketId, request));
    }

    @PostMapping("/policy-guidance")
    public ResponseEntity<PolicyGuidanceResponse> getPolicyGuidance(
            @PathVariable String ticketId,
            @Valid @RequestBody PolicyGuidanceRequest request) {
        log.info("POST /api/ai/tickets/{}/policy-guidance", ticketId);
        return ResponseEntity.ok(policyGuidanceService.getGuidance(ticketId, request));
    }
}
