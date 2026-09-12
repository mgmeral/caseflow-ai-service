package com.caseflow.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageItem {
    private String direction;
    private String from;
    private List<String> recipients;
    private String subject;
    private String preview;
    private String sentAt;
}
