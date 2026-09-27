package com.caseflow.ai.api.dto;

import com.caseflow.ai.domain.MessageItem;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyGuidanceRequest {
    @NotBlank
    private String query;
    private String customerName;
    /** Scopes policy retrieval to GLOBAL policies plus this customer's own. Optional. */
    private String customerId;
    @NotBlank
    private String ticketStatus;
    @NotBlank
    private String priority;
    private List<String> tags;
    private List<MessageItem> latestMessages;
    @Builder.Default
    private Integer topK = 5;
}
