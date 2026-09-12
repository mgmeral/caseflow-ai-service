package com.caseflow.ai.support;

/**
 * Strips common LLM output decorations from a raw response so that Jackson can parse
 * the inner JSON object cleanly.
 *
 * Handles:
 *   - UTF-8 BOM
 *   - Surrounding markdown fences (```json, ```JSON, ```)
 *   - Trailing fence identifiers like ``` id="abc"
 *   - Explanatory prefix/suffix text by extracting from first '{' to last '}'
 */
public final class LlmJsonSanitizer {

    static final int SNIPPET_MAX = 1000;

    private LlmJsonSanitizer() {}

    /**
     * Returns best-effort cleaned JSON string, or the original input unchanged.
     * Never throws. Null-safe.
     */
    public static String sanitize(String raw) {
        if (raw == null) return null;

        String s = raw;

        // Remove UTF-8 BOM
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1);
        }

        s = s.strip();

        // Strip markdown fence wrapper: opening line (```json, ```JSON, ```) + closing ```
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence);
            }
            s = s.strip();
        }

        // Extract from first '{' to last '}' to discard any remaining prefix/suffix text
        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            s = s.substring(firstBrace, lastBrace + 1);
        }

        return s;
    }

    /** Returns a log-safe snippet of at most SNIPPET_MAX characters. */
    public static String snippet(String s) {
        if (s == null) return "(null)";
        return s.length() <= SNIPPET_MAX ? s : s.substring(0, SNIPPET_MAX) + "...[truncated]";
    }
}
