package com.caseflow.ai.service.prompt;

import com.caseflow.ai.api.dto.ReplyDraftRequest;
import com.caseflow.ai.support.PromptUtils;
import org.springframework.stereotype.Component;

@Component
public class ReplyDraftPromptBuilder {

    public String build(ReplyDraftRequest request) {
        String tone = request.getTone() != null ? request.getTone() : "PROFESSIONAL";
        String goal = request.getReplyGoal() != null ? request.getReplyGoal() : "RESOLUTION";

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

                Internal Notes:
                %s

                Relevant Policy Snippets:
                %s

                Constraints:
                %s

                Draft a reply with the specified tone (%s) and goal (%s).
                Respond with a valid JSON object matching this schema exactly:
                {
                  "suggestedSubject": "<email subject line>",
                  "suggestedBody": "<full reply body>",
                  "reasoningSummary": "<brief explanation of the reply approach>",
                  "warnings": ["<warning if any>"],
                  "suggestedTags": ["<tag1>"],
                  "suggestedPriority": "<priority level>",
                  "confidence": <0.0 to 1.0>
                }

                Return ONLY the JSON object, no additional text.
                """.formatted(
                orDefault(request.getCustomerName(), "Customer"),
                request.getTicketStatus(),
                request.getPriority(),
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
                goal
        );
    }

    private String orDefault(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
