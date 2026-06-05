package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeductionStartResponse {
    private Long sessionId;
    private Integer maxQuestionCount;
    private Integer currentQuestionCount;
    private List<String> collectedClues;
    private String finalQuestion;
    private String message;
}
