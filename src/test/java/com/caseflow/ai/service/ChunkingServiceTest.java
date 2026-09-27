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
        chunkingService = service(100, 40);
    }

    private static ChunkingService service(int size, int overlap) {
        AppConfig config = new AppConfig();
        config.setChunkSize(size);
        config.setChunkOverlap(overlap);
        return new ChunkingService(config);
    }

    @Test
    void chunk_emptyText_returnsEmptyList() {
        assertThat(chunkingService.chunk("")).isEmpty();
        assertThat(chunkingService.chunk("  \n ")).isEmpty();
    }

    @Test
    void chunk_nullText_returnsEmptyList() {
        assertThat(chunkingService.chunk(null)).isEmpty();
    }

    @Test
    void chunk_shortText_returnsSingleChunk() {
        String text = "Short text.";
        assertThat(chunkingService.chunk(text)).containsExactly(text);
    }

    @Test
    void chunk_neverCutsASentence_andRespectsSize() {
        String text = "Customer cannot log in after the update. "
                + "Password reset emails do not arrive. "
                + "The SPF record of the sender domain was missing. "
                + "Adding SPF and DKIM fixed delivery. "
                + "Customer confirmed the fix on Monday.";

        List<String> chunks = chunkingService.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        chunks.forEach(c -> {
            assertThat(c.length()).isLessThanOrEqualTo(100);
            assertThat(c).endsWith(".");
        });
    }

    @Test
    void chunk_repeatsTrailingSentenceAsOverlap() {
        String text = "Aaaa aaaa aaaa aaaa aaaa aaaa. Bbbb bbbb bbbb bbbb. Cccc cccc cccc.";

        List<String> chunks = service(60, 25).chunk(text);

        assertThat(chunks).containsExactly(
                "Aaaa aaaa aaaa aaaa aaaa aaaa. Bbbb bbbb bbbb bbbb.",
                "Bbbb bbbb bbbb bbbb. Cccc cccc cccc.");
    }

    @Test
    void chunk_skipsOverlapThatWouldNotFitNextToTheNextSentence() {
        String text = "Aaaa aaaa aaaa aaaa aaaa aaaa. Bbbb bbbb bbbb bbbb. "
                + "Cccc cccc cccc cccc cccc cccc cccc cccc cccc.";

        List<String> chunks = service(60, 25).chunk(text);

        assertThat(chunks).containsExactly(
                "Aaaa aaaa aaaa aaaa aaaa aaaa. Bbbb bbbb bbbb bbbb.",
                "Cccc cccc cccc cccc cccc cccc cccc cccc cccc.");
    }

    @Test
    void chunk_keepsAllTextOfAnOverlongSentence() {
        String sentence = "x".repeat(250);

        List<String> chunks = chunkingService.chunk(sentence);

        assertThat(String.join("", chunks)).isEqualTo(sentence);
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(100));
    }

    @Test
    void chunk_preservesParagraphBreaksAndTurkishText() {
        String text = "Şifre sıfırlama çalışmıyor.\n\nMüşteri yarın tekrar deneyecek.";

        assertThat(chunkingService.chunk(text))
                .containsExactly("Şifre sıfırlama çalışmıyor.\n\nMüşteri yarın tekrar deneyecek.");
    }

    @Test
    void chunk_everySentenceAppearsInSomeChunk() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("Sentence number ").append(i).append(" is here. ");

        List<String> chunks = chunkingService.chunk(sb.toString());

        for (int i = 0; i < 40; i++) {
            String s = "Sentence number " + i + " is here.";
            assertThat(chunks).anyMatch(c -> c.contains(s));
        }
    }
}
