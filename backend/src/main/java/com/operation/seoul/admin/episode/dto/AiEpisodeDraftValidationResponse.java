package com.operation.seoul.admin.episode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEpisodeDraftValidationResponse {
    private boolean valid;
    private int riskScore;
    private String summary;
    private List<Finding> findings;
    private List<String> requiredFixes;
    private List<String> publishChecklist;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finding {
        private String severity;
        private String code;
        private String message;
        private Integer missionOrder;
    }
}
