package com.caseflow.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyDraftResponse {
    private String ticketId;
    private String suggestedSubject;
    private String suggestedBody;
    private String reasoningSummary;
    private List<String> warnings;
    private List<String> suggestedTags;
    private String suggestedPriority;
    private Double confidence;
}
