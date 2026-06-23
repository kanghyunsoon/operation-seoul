package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeductionAskResponse {
    private String answerType;
    private String answerText;
    private Integer questionCount;
    private Integer remainingQuestionCount;
    private Integer clearTimePenaltySeconds;
}
