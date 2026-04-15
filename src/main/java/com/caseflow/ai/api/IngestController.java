package com.caseflow.ai.api;

import com.caseflow.ai.api.dto.DocumentIngestRequest;
import com.caseflow.ai.api.dto.IngestResponse;
import com.caseflow.ai.api.dto.TicketIngestRequest;
import com.caseflow.ai.service.ingest.DocumentIngestService;
import com.caseflow.ai.service.ingest.TicketIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/ingest")
@RequiredArgsConstructor
@Validated
@Slf4j
public class IngestController {

    private final DocumentIngestService documentIngestService;
    private final TicketIngestService ticketIngestService;

    @PostMapping("/documents")
    public ResponseEntity<IngestResponse> ingestDocument(
            @Valid @RequestBody DocumentIngestRequest request) {
        log.info("POST /api/ai/ingest/documents sourceId={}", request.getSourceId());
        return ResponseEntity.ok(documentIngestService.ingest(request));
    }

    @PostMapping("/tickets")
    public ResponseEntity<IngestResponse> ingestTicket(
            @Valid @RequestBody TicketIngestRequest request) {
        log.info("POST /api/ai/ingest/tickets sourceId={}", request.getSourceId());
        return ResponseEntity.ok(ticketIngestService.ingest(request));
    }
}
