package com.caseflow.ai.support;

import com.caseflow.ai.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final AppConfig config;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        int chunkSize = config.getChunkSize();
        int overlap = config.getChunkOverlap();

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += (chunkSize - overlap);
            if (start >= text.length()) {
                break;
            }
        }
        return chunks;
    }
}
