package com.caseflow.ai.support;

import com.caseflow.ai.domain.MessageItem;

import java.util.List;

public final class PromptUtils {

    private PromptUtils() {}

    public static String formatMessages(List<MessageItem> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(no messages)";
        }
        StringBuilder sb = new StringBuilder();
        for (MessageItem msg : messages) {
            sb.append("---\n");
            sb.append("Direction: ").append(msg.getDirection()).append("\n");
            sb.append("From: ").append(msg.getSender()).append("\n");
            if (msg.getSubject() != null) {
                sb.append("Subject: ").append(msg.getSubject()).append("\n");
            }
            if (msg.getCreatedAt() != null) {
                sb.append("Date: ").append(msg.getCreatedAt()).append("\n");
            }
            sb.append("Body:\n").append(msg.getBody()).append("\n");
        }
        return sb.toString();
    }

    public static String formatList(List<String> items, String label) {
        if (items == null || items.isEmpty()) {
            return label + ": (none)";
        }
        return label + ":\n- " + String.join("\n- ", items);
    }
}
