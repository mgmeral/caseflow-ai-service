package com.caseflow.ai.service.prompt;

import com.caseflow.ai.api.dto.TicketSummaryRequest;
import com.caseflow.ai.support.PromptUtils;
import org.springframework.stereotype.Component;

@Component
public class SummaryPromptBuilder {

    public String build(TicketSummaryRequest request) {
        String style = request.getSummaryStyle() != null ? request.getSummaryStyle() : "STANDARD";

        return """
                You are an expert customer support analyst. Analyze the following support ticket and produce a structured summary.

                Ticket Context:
                - Customer: %s
                - Status: %s
                - Priority: %s
                - SLA State: %s
                - Tags: %s
                - Locale: %s
                - Summary Style: %s

                Messages:
                %s

                Internal Notes:
                %s

                Respond with a valid JSON object matching this schema exactly:
                {
                  "summary": "<concise summary of the ticket>",
                  "customerIntent": "<what the customer is trying to achieve>",
                  "keyPoints": ["<point1>", "<point2>"],
                  "riskSignals": ["<risk1>"],
                  "suggestedNextStep": "<recommended action>",
                  "confidence": <0.0 to 1.0>,
                  "citations": ["<message ref or note ref>"]
                }

                Style guidance for %s:
                - SHORT: 1-2 sentence summary, minimal keyPoints
                - STANDARD: 3-5 sentence summary, up to 5 keyPoints
                - DETAILED: comprehensive summary, all keyPoints and riskSignals

                Return ONLY the JSON object, no additional text.
                """.formatted(
                orDefault(request.getCustomerName(), "Unknown"),
                request.getTicketStatus(),
                request.getPriority(),
                orDefault(request.getSlaState(), "N/A"),
                orDefault(request.getTags() != null ? String.join(", ", request.getTags()) : null, "none"),
                orDefault(request.getLocale(), "en"),
                style,
                PromptUtils.formatMessages(request.getLatestMessages()),
                PromptUtils.formatList(request.getInternalNotes(), "Notes"),
                style
        );
    }

    private String orDefault(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
