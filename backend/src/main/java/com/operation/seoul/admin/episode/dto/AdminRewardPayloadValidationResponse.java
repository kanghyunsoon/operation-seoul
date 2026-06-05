package com.operation.seoul.admin.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminRewardPayloadValidationResponse {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private List<RewardItem> rewards;

    @Data
    @Builder
    public static class RewardItem {
        private String type;
        private String value;
        private Long targetId;
        private String targetLabel;
    }
}