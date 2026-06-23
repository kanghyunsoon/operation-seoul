package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinalAnswerResponse {
    private boolean correct;
    private String status;
    private Integer score;
    private Integer clearTimePenaltySeconds;
    private String message;
}
