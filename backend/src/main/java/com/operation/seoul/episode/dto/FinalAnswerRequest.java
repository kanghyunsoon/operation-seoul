package com.operation.seoul.episode.dto;

import lombok.Data;

@Data
public class FinalAnswerRequest {
    private Long sessionId;
    private String finalAnswer;
}
