package com.caseflow.ai.support;

import com.caseflow.ai.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits text into chunks along paragraph and sentence boundaries.
 *
 * <p>Sentences are packed greedily into chunks of at most {@code caseflow.ai.chunk-size}
 * characters, so a chunk never ends mid-sentence. Each new chunk repeats the trailing whole
 * sentences of the previous one, up to {@code chunk-overlap} characters, so a fact spanning a
 * boundary is still retrievable. Only a single sentence longer than the chunk size is cut
 * by characters. Paragraph breaks are preserved inside chunks.
 */
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    /** After sentence-final punctuation, or at a single line break (lists, headers, signatures). */
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?…])\\s+|\\n");

    private final AppConfig config;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        int chunkSize = config.getChunkSize();
        int overlap = Math.min(config.getChunkOverlap(), chunkSize / 2);

        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentLength = 0;

        for (String unit : units(text.replace("\r\n", "\n"), chunkSize)) {
            int added = (current.isEmpty() ? 0 : 1) + unit.length();
            if (!current.isEmpty() && currentLength + added > chunkSize) {
                chunks.add(join(current));
                current = overlapTail(current, overlap, chunkSize - unit.length() - 1);
                currentLength = rawLength(current);
                added = (current.isEmpty() ? 0 : 1) + unit.length();
            }
            current.add(unit);
            currentLength += added;
        }
        if (!current.isEmpty()) {
            chunks.add(join(current));
        }
        return chunks;
    }

    /**
     * Sentences, with a paragraph end marked by a trailing "\n" so {@link #join} restores the
     * blank line. A sentence longer than {@code chunkSize} is cut into chunk-sized pieces.
     */
    private List<String> units(String text, int chunkSize) {
        List<String> units = new ArrayList<>();
        for (String paragraph : PARAGRAPH_BREAK.split(text.strip())) {
            List<String> sentences = new ArrayList<>();
            // Pieces of at most chunkSize - 1, so a paragraph-final piece plus its "\n" still fits.
            int piece = Math.max(1, chunkSize - 1);
            for (String sentence : SENTENCE_BREAK.split(paragraph.strip())) {
                String s = sentence.strip();
                for (int i = 0; i < s.length(); i += piece) {
                    sentences.add(s.substring(i, Math.min(s.length(), i + piece)));
                }
            }
            if (sentences.isEmpty()) continue;
            sentences.set(sentences.size() - 1, sentences.get(sentences.size() - 1) + "\n");
            units.addAll(sentences);
        }
        return units;
    }

    /** Trailing whole sentences of the finished chunk, within the overlap and the room left. */
    private List<String> overlapTail(List<String> finished, int overlap, int room) {
        List<String> tail = new ArrayList<>();
        int length = 0;
        for (int i = finished.size() - 1; i > 0; i--) {
            String sentence = finished.get(i);
            int next = length + sentence.length() + (tail.isEmpty() ? 0 : 1);
            if (next > overlap || next > room) break;
            tail.add(0, sentence);
            length = next;
        }
        return tail;
    }

    /** Length of {@link #join} before its final strip: units plus one separator between each. */
    private int rawLength(List<String> units) {
        return units.stream().mapToInt(String::length).sum() + Math.max(0, units.size() - 1);
    }

    private String join(List<String> units) {
        StringBuilder sb = new StringBuilder();
        for (String unit : units) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append(' ');
            else if (sb.length() > 0) sb.append('\n');
            sb.append(unit);
        }
        return sb.toString().strip();
    }
}
