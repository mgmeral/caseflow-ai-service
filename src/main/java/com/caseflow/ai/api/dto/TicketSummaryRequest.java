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
public class TicketSummaryRequest {
    private String customerName;
    @NotBlank
    private String ticketStatus;
    @NotBlank
    private String priority;
    private List<String> tags;
    @NotEmpty
    private List<MessageItem> latestMessages;
    private List<String> internalNotes;
    private String slaState;
    private String locale;
    private String summaryStyle;
}
