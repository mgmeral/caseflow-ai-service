package com.caseflow.ai.service.prompt;

import com.caseflow.ai.api.dto.ReplyDraftRequest;
import com.caseflow.ai.support.PromptUtils;
import org.springframework.stereotype.Component;

@Component
public class ReplyDraftPromptBuilder {

    public String build(ReplyDraftRequest request) {
        String tone = request.getTone() != null && !request.getTone().isBlank()
                ? request.getTone() : "PROFESSIONAL";
        String goal = request.getReplyGoal() != null && !request.getReplyGoal().isBlank()
                ? request.getReplyGoal() : "RESOLUTION";

        return """
                You are a senior customer support specialist drafting a reply to a customer.

                Ticket Context:
                - Customer: %s
                - Status: %s
                - Priority: %s
                - Locale: %s
                - Tone: %s
                - Reply Goal: %s
                - Tags: %s
                - Template Code: %s

                Conversation History:
                %s

                Supplementary Internal Notes (do NOT copy into the reply):
                %s

                Supplementary Policy Reference (use for grounding only, do NOT quote verbatim):
                %s

                Constraints:
                %s

                Instructions:
                - Write a polite, professional reply to the customer in the specified tone (%s) aimed at %s.
                - The suggestedBody field must contain ONLY the proposed reply text — no markdown wrappers, no headings, no explanations.
                - Do NOT make promises about specific SLA timelines or resolution dates unless explicitly stated in the conversation.
                - Do NOT fabricate actions that have not been taken.
                - Do NOT leak internal notes or policy snippet text verbatim into the reply.
                - Do NOT include any greeting or sign-off boilerplate beyond what fits naturally in the draft.
                - Reply must be safe to show to the customer without further redaction.
                - %s

                Respond with a valid JSON object matching this schema exactly:
                {
                  "suggestedSubject": "<email subject line>",
                  "suggestedBody": "<customer-safe reply body text only>",
                  "reasoningSummary": "<brief internal explanation of the approach>",
                  "warnings": ["<warning if any constraint was violated or context was insufficient>"],
                  "suggestedTags": ["<tag1>"],
                  "suggestedPriority": "<priority level>",
                  "confidence": <0.0 to 1.0>
                }

                Return ONLY the JSON object, no additional text.
                """.formatted(
                orDefault(request.getCustomerName(), "Customer"),
                orDefault(request.getTicketStatus(), "UNKNOWN"),
                orDefault(request.getPriority(), "UNKNOWN"),
                orDefault(request.getLocale(), "en"),
                tone,
                goal,
                orDefault(request.getTags() != null ? String.join(", ", request.getTags()) : null, "none"),
                orDefault(request.getSelectedTemplateCode(), "none"),
                PromptUtils.formatMessages(request.getLatestMessages()),
                PromptUtils.formatList(request.getInternalNotes(), "Notes"),
                PromptUtils.formatList(request.getPolicySnippets(), "Policies"),
                PromptUtils.formatList(request.getConstraints(), "Constraints"),
                tone,
                goal,
                PromptUtils.languageInstruction(request.getLocale())
        );
    }

    private String orDefault(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
