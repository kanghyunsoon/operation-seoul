package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeductionQuestionResponse {
    private Long id;
    private String userQuestion;
    private String aiAnswerType;
    private String aiAnswerText;
    private LocalDateTime createdAt;
}
