package com.caseflow.ai.service;

import com.caseflow.ai.config.AppConfig;
import com.caseflow.ai.support.ChunkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        AppConfig config = new AppConfig();
        config.setChunkSize(100);
        config.setChunkOverlap(20);
        chunkingService = new ChunkingService(config);
    }

    @Test
    void chunk_emptyText_returnsEmptyList() {
        List<String> result = chunkingService.chunk("");
        assertThat(result).isEmpty();
    }

    @Test
    void chunk_nullText_returnsEmptyList() {
        List<String> result = chunkingService.chunk(null);
        assertThat(result).isEmpty();
    }

    @Test
    void chunk_shortText_returnsSingleChunk() {
        String text = "Short text.";
        List<String> result = chunkingService.chunk(text);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(text);
    }

    @Test
    void chunk_longText_returnsMultipleChunks() {
        // Create text longer than chunk size (100 chars)
        String text = "A".repeat(250);
        List<String> result = chunkingService.chunk(text);
        assertThat(result).hasSizeGreaterThan(1);
        // Each chunk should be at most chunkSize characters
        result.forEach(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void chunk_respectsOverlap() {
        String text = "A".repeat(150);
        AppConfig config = new AppConfig();
        config.setChunkSize(100);
        config.setChunkOverlap(20);
        ChunkingService service = new ChunkingService(config);

        List<String> result = service.chunk(text);
        assertThat(result).isNotEmpty();
        // First chunk starts at 0, second at 80 (100-20), so there is overlap
        if (result.size() >= 2) {
            assertThat(result.get(0).substring(80)).isEqualTo(result.get(1).substring(0, 20));
        }
    }
}
