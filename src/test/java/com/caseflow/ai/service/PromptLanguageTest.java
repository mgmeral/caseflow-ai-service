package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.ReplyDraftRequest;
import com.caseflow.ai.api.dto.TicketSummaryRequest;
import com.caseflow.ai.domain.MessageItem;
import com.caseflow.ai.service.prompt.ReplyDraftPromptBuilder;
import com.caseflow.ai.service.prompt.SummaryPromptBuilder;
import com.caseflow.ai.support.PromptUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prompts must tell the model which language to write in; a bare "Locale: tr" context
 * line was ignored by small models, so Turkish tickets got English summaries.
 */
class PromptLanguageTest {

    private static final List<MessageItem> MESSAGES = List.of(
            MessageItem.builder().direction("inbound").preview("Şifremi sıfırlayamıyorum.").build());

    @Test
    void languageInstruction_namesTheLanguage() {
        assertThat(PromptUtils.languageInstruction("tr")).contains("Turkish").contains("\"tr\"");
        assertThat(PromptUtils.languageInstruction("en")).contains("English");
        assertThat(PromptUtils.languageInstruction(null)).contains("English");
    }

    @Test
    void summaryPrompt_instructsTicketLanguage() {
        String prompt = new SummaryPromptBuilder().build(TicketSummaryRequest.builder()
                .locale("tr").latestMessages(MESSAGES).build());

        assertThat(prompt).contains("Write every text value in Turkish");
    }

    @Test
    void replyDraftPrompt_instructsTicketLanguage() {
        String prompt = new ReplyDraftPromptBuilder().build(ReplyDraftRequest.builder()
                .locale("tr").latestMessages(MESSAGES).build());

        assertThat(prompt).contains("Write every text value in Turkish");
    }
}
