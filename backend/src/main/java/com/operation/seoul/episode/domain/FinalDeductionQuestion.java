package com.operation.seoul.episode.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FinalDeductionQuestion {
    private Long id;
    private Long sessionId;
    private String userQuestion;
    private String aiAnswerType;
    private String aiAnswerText;
    private LocalDateTime createdAt;
}
