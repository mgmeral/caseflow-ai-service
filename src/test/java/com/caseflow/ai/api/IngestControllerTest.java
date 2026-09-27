package com.caseflow.ai.api;

import com.caseflow.ai.service.ingest.DocumentIngestService;
import com.caseflow.ai.service.ingest.TicketIngestService;
import com.caseflow.ai.service.ingest.VectorIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
class IngestControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private DocumentIngestService documentIngestService;
    @MockBean private TicketIngestService ticketIngestService;
    @MockBean private VectorIngestionService vectorIngestionService;

    @Test
    void deleteSource_removesAllChunksOfThatSource() throws Exception {
        mockMvc.perform(delete("/api/ai/ingest/TICKET/3f0c-uuid"))
                .andExpect(status().isNoContent());

        verify(vectorIngestionService).deleteSource("TICKET", "3f0c-uuid");
    }
}
