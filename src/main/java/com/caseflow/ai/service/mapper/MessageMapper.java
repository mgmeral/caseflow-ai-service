package com.caseflow.ai.service.mapper;

import com.caseflow.ai.domain.MessageItem;
import com.caseflow.ai.support.PromptUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageMapper {

    public String toFormattedString(List<MessageItem> messages) {
        return PromptUtils.formatMessages(messages);
    }

    public String toSingleString(MessageItem message) {
        if (message == null) {
            return "";
        }
        return "[" + message.getDirection() + "] " + message.getSender() + ": " + message.getBody();
    }
}
