package com.caseflow.ai.service;

import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.service.rag.RetrievalFilter;
import com.caseflow.ai.service.rag.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetrievalServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private AppConfig appConfig;

    private RetrievalService service;

    @BeforeEach
    void setUp() {
        AppConfig.Retrieval retrieval = new AppConfig.Retrieval();
        when(appConfig.getRetrieval()).thenReturn(retrieval);
        service = new RetrievalService(vectorStore, appConfig);
    }

    @Test
    void search_returnsEmptyResultWithWarning_whenVectorStoreReturnsNothing() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        RetrievalService.RetrievalResult result = service.search("test query", 5, RetrievalFilter.ofSourceType("POLICY"));

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.documents()).isEmpty();
        assertThat(result.warning()).isNotBlank();
        assertThat(result.warning()).contains("policy");
    }

    @Test
    void search_returnsDocuments_whenVectorStoreHasMatches() {
        Document doc = Document.builder().text("Policy content.")
                .metadata(Map.of("sourceId", "P1", "sourceType", "POLICY")).score(0.88).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        RetrievalService.RetrievalResult result = service.search("refund policy", 5, RetrievalFilter.ofSourceType("POLICY"));

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.documents()).hasSize(1);
        assertThat(result.warning()).isNull();
    }

    @Test
    void search_postFiltersResults_bySourceType() {
        // Vector store returns mixed types; post-filter should keep only POLICY
        Document policyDoc = Document.builder().text("Policy A")
                .metadata(Map.of("sourceId", "P1", "sourceType", "POLICY")).score(0.9).build();
        Document ticketDoc = Document.builder().text("Ticket B")
                .metadata(Map.of("sourceId", "T1", "sourceType", "TICKET")).score(0.85).build();

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(policyDoc, ticketDoc));

        RetrievalService.RetrievalResult result = service.search("refund", 5, RetrievalFilter.ofSourceType("POLICY"));

        assertThat(result.documents()).hasSize(1);
        assertThat(result.documents().get(0).getMetadata().get("sourceType")).isEqualTo("POLICY");
    }

    @Test
    void search_returnsEmptyWithGeneralWarning_whenNoFilterAndNoResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        RetrievalService.RetrievalResult result = service.search("some query", 5, null);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.warning()).contains("vector store may be empty");
    }

    @Test
    void search_pushesFilterDownToVectorStore() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        service.search("q", 5, new RetrievalFilter("TICKET", List.of("c1"), null, null, null));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression()).isNotNull();
        assertThat(captor.getValue().getFilterExpression().toString()).contains("customerId", "c1");
        assertThat(captor.getValue().getTopK()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void search_dropsDocumentsOutsideCustomerScope_evenIfStoreReturnsThem() {
        Document mine = Document.builder().text("mine")
                .metadata(Map.of("sourceType", "TICKET", "sourceId", "t1", "customerId", "c1")).score(0.9).build();
        Document other = Document.builder().text("other customer")
                .metadata(Map.of("sourceType", "TICKET", "sourceId", "t2", "customerId", "c2")).score(0.95).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(other, mine));

        RetrievalService.RetrievalResult result =
                service.search("q", 5, new RetrievalFilter("TICKET", List.of("c1"), null, null, null));

        assertThat(result.documents()).extracting(d -> d.getMetadata().get("sourceId")).containsExactly("t1");
    }

    @Test
    void search_propagatesStoreFailure_insteadOfRetryingUnfiltered() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("qdrant down"));

        assertThatThrownBy(() -> service.search("q", 5, RetrievalFilter.policiesFor("c1")))
                .hasMessageContaining("qdrant down");
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void search_staticEmptyFactory_createsEmptyResultWithWarning() {
        RetrievalService.RetrievalResult result =
                RetrievalService.RetrievalResult.empty("No policy docs found");

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.documents()).isEmpty();
        assertThat(result.warning()).isEqualTo("No policy docs found");
    }
}
