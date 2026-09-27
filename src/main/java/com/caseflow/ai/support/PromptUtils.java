package com.caseflow.ai.support;

import com.caseflow.ai.domain.MessageItem;

import java.util.List;
import java.util.Locale;

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
            sb.append("From: ").append(msg.getFrom()).append("\n");
            if (msg.getSubject() != null) {
                sb.append("Subject: ").append(msg.getSubject()).append("\n");
            }
            if (msg.getSentAt() != null) {
                sb.append("Date: ").append(msg.getSentAt()).append("\n");
            }
            sb.append("Preview:\n").append(msg.getPreview()).append("\n");
        }
        return sb.toString();
    }

    /**
     * The "Locale" context line alone does not make small models answer in that language;
     * this explicit instruction does. JSON keys stay English so parsing is unaffected.
     */
    public static String languageInstruction(String locale) {
        String code = locale != null && !locale.isBlank() ? locale : "en";
        String language = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH);
        if (language.isBlank()) language = code;
        return "Write every text value in " + language + " (locale \"" + code + "\"). "
                + "Keep the JSON keys exactly as shown, in English.";
    }

    public static String formatList(List<String> items, String label) {
        if (items == null || items.isEmpty()) {
            return label + ": (none)";
        }
        return label + ":\n- " + String.join("\n- ", items);
    }
}
