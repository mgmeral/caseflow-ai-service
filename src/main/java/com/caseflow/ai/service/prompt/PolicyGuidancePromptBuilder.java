package com.caseflow.ai.service.prompt;

import com.caseflow.ai.api.dto.PolicyGuidanceRequest;
import com.caseflow.ai.support.PromptUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PolicyGuidancePromptBuilder {

    public String build(PolicyGuidanceRequest request, List<String> retrievedSnippets) {
        return """
                You are a compliance and policy expert for a customer support team.
                Answer the following question using ONLY the provided policy snippets.

                Question: %s

                Ticket Context:
                - Customer: %s
                - Status: %s
                - Priority: %s
                - Tags: %s

                Conversation History:
                %s

                Retrieved Policy Snippets:
                %s

                Based strictly on the policy snippets above, respond with a valid JSON object:
                {
                  "answer": "<direct answer to the question>",
                  "recommendedActions": ["<action1>", "<action2>"],
                  "confidence": <0.0 to 1.0>,
                  "warnings": ["<warning if policy is ambiguous or missing>"]
                }

                Return ONLY the JSON object, no additional text.
                """.formatted(
                request.getQuery(),
                orDefault(request.getCustomerName(), "Unknown"),
                request.getTicketStatus(),
                request.getPriority(),
                orDefault(request.getTags() != null ? String.join(", ", request.getTags()) : null, "none"),
                PromptUtils.formatMessages(request.getLatestMessages()),
                formatSnippets(retrievedSnippets)
        );
    }

    private String formatSnippets(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return "(no policy snippets retrieved)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snippets.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(snippets.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    private String orDefault(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
