package com.caseflow.ai.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LlmJsonSanitizerTest {

    private static final String PLAIN_JSON = """
            {"summary":"ok","confidence":0.9}""";

    // ── null / empty ──────────────────────────────────────────────────────────

    @Test
    void sanitize_returnsNull_whenInputIsNull() {
        assertThat(LlmJsonSanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_returnsBlank_whenInputIsBlank() {
        assertThat(LlmJsonSanitizer.sanitize("   ")).isBlank();
    }

    // ── plain JSON ────────────────────────────────────────────────────────────

    @Test
    void sanitize_returnsUnchanged_whenPlainJson() {
        assertThat(LlmJsonSanitizer.sanitize(PLAIN_JSON)).isEqualTo(PLAIN_JSON);
    }

    // ── markdown fences ───────────────────────────────────────────────────────

    @Test
    void sanitize_stripsJsonFence() {
        String fenced = "```json\n" + PLAIN_JSON + "\n```";
        assertThat(LlmJsonSanitizer.sanitize(fenced)).isEqualTo(PLAIN_JSON);
    }

    @Test
    void sanitize_stripsJsonFenceCaseInsensitive() {
        String fenced = "```JSON\n" + PLAIN_JSON + "\n```";
        assertThat(LlmJsonSanitizer.sanitize(fenced)).isEqualTo(PLAIN_JSON);
    }

    @Test
    void sanitize_stripsPlainFence() {
        String fenced = "```\n" + PLAIN_JSON + "\n```";
        assertThat(LlmJsonSanitizer.sanitize(fenced)).isEqualTo(PLAIN_JSON);
    }

    @Test
    void sanitize_stripsTrailingFenceWithIdentifier() {
        // Observed real-world pattern: ``` id="839jlwm" after closing fence
        String fenced = "```json\n" + PLAIN_JSON + "\n``` id=\"839jlwm\"";
        String result = LlmJsonSanitizer.sanitize(fenced);
        assertThat(result).isEqualTo(PLAIN_JSON);
    }

    // ── explanatory text prefix/suffix ────────────────────────────────────────

    @Test
    void sanitize_stripsExplanatoryPrefixText() {
        String withPrefix = "Here is the JSON object you requested:\n" + PLAIN_JSON;
        assertThat(LlmJsonSanitizer.sanitize(withPrefix)).isEqualTo(PLAIN_JSON);
    }

    @Test
    void sanitize_stripsExplanatorySuffixText() {
        String withSuffix = PLAIN_JSON + "\nLet me know if you need anything else.";
        assertThat(LlmJsonSanitizer.sanitize(withSuffix)).isEqualTo(PLAIN_JSON);
    }

    @Test
    void sanitize_stripsBothPrefixAndSuffix() {
        String wrapped = "Sure! Here is the summary:\n" + PLAIN_JSON + "\nHope that helps.";
        assertThat(LlmJsonSanitizer.sanitize(wrapped)).isEqualTo(PLAIN_JSON);
    }

    @Test
    void sanitize_handlesFencePlusPrefixText() {
        // Fence present but also extra text before the JSON inside the fence
        String messy = "```json\nHere you go:\n" + PLAIN_JSON + "\n```";
        String result = LlmJsonSanitizer.sanitize(messy);
        assertThat(result).isEqualTo(PLAIN_JSON);
    }

    // ── UTF-8 BOM ─────────────────────────────────────────────────────────────

    @Test
    void sanitize_removesUtf8Bom() {
        String withBom = "\uFEFF" + PLAIN_JSON;
        assertThat(LlmJsonSanitizer.sanitize(withBom)).isEqualTo(PLAIN_JSON);
    }

    // ── full realistic example from bug report ────────────────────────────────

    @Test
    void sanitize_handlesRealWorldFencedOutput() {
        String raw = """
                ```json
                {
                  "summary": "Customer BTC TURK is unable to send a chain command.",
                  "customerIntent": "Resolve issue with sending chain commands",
                  "keyPoints": ["Ticket status: TRIAGED, SLA BREACHED"],
                  "riskSignals": ["SLA breach"],
                  "suggestedNextStep": "Verify account settings",
                  "confidence": 0.7,
                  "citations": ["user@example.com"]
                }
                ``` id="839jlwm"
                """;

        String result = LlmJsonSanitizer.sanitize(raw);

        assertThat(result).startsWith("{");
        assertThat(result).endsWith("}");
        assertThat(result).contains("\"summary\"");
        assertThat(result).contains("BTC TURK");
        assertThat(result).doesNotContain("```");
        assertThat(result).doesNotContain("id=\"839jlwm\"");
    }

    // ── malformed input — still fails after sanitization ─────────────────────

    @Test
    void sanitize_returnsInput_whenNoJsonObjectPresent() {
        String noJson = "The model is currently unavailable.";
        // No braces → brace-extraction branch skipped; just returns trimmed input
        String result = LlmJsonSanitizer.sanitize(noJson);
        assertThat(result).isEqualTo(noJson);
    }

    @Test
    void sanitize_neverThrows_onArbitraryInput() {
        assertThatCode(() -> LlmJsonSanitizer.sanitize("}{garbage}{")).doesNotThrowAnyException();
        assertThatCode(() -> LlmJsonSanitizer.sanitize("```\n```")).doesNotThrowAnyException();
        assertThatCode(() -> LlmJsonSanitizer.sanitize("\uFEFF")).doesNotThrowAnyException();
    }

    // ── snippet helper ────────────────────────────────────────────────────────

    @Test
    void snippet_returnsNullLabel_whenNull() {
        assertThat(LlmJsonSanitizer.snippet(null)).isEqualTo("(null)");
    }

    @Test
    void snippet_returnsFullString_whenUnderLimit() {
        assertThat(LlmJsonSanitizer.snippet("hello")).isEqualTo("hello");
    }

    @Test
    void snippet_truncatesLongString() {
        String long_ = "x".repeat(LlmJsonSanitizer.SNIPPET_MAX + 100);
        String result = LlmJsonSanitizer.snippet(long_);
        assertThat(result).hasSize(LlmJsonSanitizer.SNIPPET_MAX + "...[truncated]".length());
        assertThat(result).endsWith("...[truncated]");
    }
}
