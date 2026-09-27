package com.caseflow.ai.service.prompt;

import com.caseflow.ai.api.dto.TicketSummaryRequest;
import com.caseflow.ai.support.PromptUtils;
import org.springframework.stereotype.Component;

@Component
public class SummaryPromptBuilder {

    public String build(TicketSummaryRequest request) {
        String style = request.getSummaryStyle() != null ? request.getSummaryStyle() : "STANDARD";

        return """
                You are an expert customer support analyst. Produce a concise operational summary for a support agent.

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

                Instructions:
                - Focus on: the main customer issue, current ticket state, most recent customer context, and any notable risk or urgency.
                - Do not produce HTML, markdown panels, bullet lists wrapped in code blocks, or instructions for external tools.
                - Do not include agent-internal notes verbatim in the summary field.
                - Keep the summary field plain text, readable by an agent at a glance.
                - %s

                Respond with a valid JSON object matching this schema exactly:
                {
                  "summary": "<concise plain-text summary of the ticket>",
                  "customerIntent": "<what the customer is trying to achieve>",
                  "keyPoints": ["<point1>", "<point2>"],
                  "riskSignals": ["<risk1>"],
                  "suggestedNextStep": "<recommended next action for the agent>",
                  "confidence": <0.0 to 1.0>,
                  "citations": ["<message ref or note ref>"]
                }

                Style guidance for %s:
                - SHORT: 1-2 sentence summary, minimal keyPoints
                - STANDARD: 3-5 sentence summary, up to 5 keyPoints
                - DETAILED: comprehensive summary, all keyPoints and riskSignals

                STRICT OUTPUT RULES — violations break automated parsing:
                - Return ONLY the raw JSON object. No other text.
                - Do NOT wrap the JSON in markdown code fences or backticks.
                - Do NOT write ``` or ```json before or after the JSON.
                - Do NOT prefix with phrases like "Here is the JSON", "Sure!", or "Here is your summary".
                - Do NOT add any explanatory text, notes, or commentary before or after the JSON object.
                - The very first character of your response must be '{' and the very last must be '}'.
                """.formatted(
                orDefault(request.getCustomerName(), "Unknown"),
                orDefault(request.getTicketStatus(), "UNKNOWN"),
                orDefault(request.getPriority(), "UNKNOWN"),
                orDefault(request.getSlaState(), "N/A"),
                orDefault(request.getTags() != null ? String.join(", ", request.getTags()) : null, "none"),
                orDefault(request.getLocale(), "en"),
                style,
                PromptUtils.formatMessages(request.getLatestMessages()),
                PromptUtils.formatList(request.getInternalNotes(), "Notes"),
                PromptUtils.languageInstruction(request.getLocale()),
                style
        );
    }

    private String orDefault(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
