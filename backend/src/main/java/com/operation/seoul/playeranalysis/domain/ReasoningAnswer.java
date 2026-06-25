package com.operation.seoul.playeranalysis.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReasoningAnswer {
    private Long id;
    private Long userId;
    private Long missionId;
    private String question;
    private String answer;
    private LocalDateTime createdAt;
}
