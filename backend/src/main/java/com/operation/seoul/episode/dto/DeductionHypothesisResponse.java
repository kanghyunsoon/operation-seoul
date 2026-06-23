package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeductionHypothesisResponse {
    private Integer matchedSlotCount;
    private Integer totalSlotCount;
    private Integer hypothesisCount;
    private Integer remainingHypothesisCount;
    private Integer clearTimePenaltySeconds;
    private String message;
}
