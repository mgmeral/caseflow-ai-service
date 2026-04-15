package com.caseflow.ai.api.dto;

import com.caseflow.ai.domain.MessageItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyDraftRequest {
    private String customerName;
    private String locale;
    private String tone;
    private String selectedTemplateCode;
    @NotBlank
    private String ticketStatus;
    @NotBlank
    private String priority;
    private List<String> tags;
    @NotEmpty
    private List<MessageItem> latestMessages;
    private List<String> internalNotes;
    private List<String> policySnippets;
    private List<String> constraints;
    private String replyGoal;
}
