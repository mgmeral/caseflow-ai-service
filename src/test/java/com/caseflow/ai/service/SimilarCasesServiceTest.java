package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.SimilarCasesRequest;
import com.caseflow.ai.api.dto.SimilarCasesResponse;
import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.observability.AiMetrics;
import com.caseflow.ai.service.ai.SimilarCasesService;
import com.caseflow.ai.service.rag.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimilarCasesServiceTest {

    @Mock private RetrievalService retrievalService;
    @Mock private AppConfig appConfig;
    @Mock private AiMetrics aiMetrics;

    private SimilarCasesService service;

    @BeforeEach
    void setUp() {
        AppConfig.Prompt promptConfig = new AppConfig.Prompt();
        when(appConfig.getModelName()).thenReturn("test-model");
        when(appConfig.getPrompt()).thenReturn(promptConfig);
        when(appConfig.getDefaultTopK()).thenReturn(5);
        service = new SimilarCasesService(retrievalService, appConfig, aiMetrics);
    }

    @Test
    void findSimilar_returnsEmptyMatchesWithWarning_whenRetrievalIsEmpty() {
        String ticketId = "TKT-100";
        SimilarCasesRequest request = SimilarCasesRequest.builder()
                .queryText("billing issue refund request")
                .topK(5)
                .build();

        when(retrievalService.search(eq("billing issue refund request"), eq(5), eq("TICKET")))
                .thenReturn(RetrievalService.RetrievalResult.empty(
                        "No indexed ticket documents matched this query."));

        SimilarCasesResponse response = service.findSimilar(ticketId, request);

        assertThat(response.getMatches()).isEmpty();
        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getWarnings().get(0)).contains("ticket");
        assertThat(response.getRequestId()).isNotBlank();
        assertThat(response.getModel()).isEqualTo("test-model");
        verify(aiMetrics).recordSimilarCasesEmptyRetrieval();
    }

    @Test
    void findSimilar_returnsMappedMatches_whenDocumentsRetrieved() {
        String ticketId = "TKT-101";
        SimilarCasesRequest request = SimilarCasesRequest.builder()
                .queryText("password reset problem")
                .topK(3)
                .build();

        Document doc = Document.builder()
                .text("User could not reset password due to expired token.")
                .metadata(Map.of("sourceId", "TKT-OLD-1", "sourceType", "TICKET", "title", "Password Reset Issue"))
                .score(0.91).build();

        when(retrievalService.search(eq("password reset problem"), eq(3), eq("TICKET")))
                .thenReturn(new RetrievalService.RetrievalResult(List.of(doc), false, null));

        SimilarCasesResponse response = service.findSimilar(ticketId, request);

        assertThat(response.getMatches()).hasSize(1);
        assertThat(response.getMatches().get(0).getSourceId()).isEqualTo("TKT-OLD-1");
        assertThat(response.getMatches().get(0).getScore()).isNotNull().isGreaterThan(0.9);
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getLatencyMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    void findSimilar_usesDefaultTopK_whenNotSpecified() {
        String ticketId = "TKT-102";
        SimilarCasesRequest request = SimilarCasesRequest.builder()
                .queryText("query")
                .topK(null)
                .build();

        when(retrievalService.search(any(), eq(5), eq("TICKET")))
                .thenReturn(new RetrievalService.RetrievalResult(Collections.emptyList(), true,
                        "No indexed ticket documents matched this query."));

        service.findSimilar(ticketId, request);

        verify(retrievalService).search(any(), eq(5), eq("TICKET"));
    }
}
