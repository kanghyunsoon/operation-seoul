package com.operation.seoul.playeranalysis.dto;

import lombok.Data;

import java.util.List;

@Data
public class PlayerAnalysisRequest {
    private Long missionId;
    private Long userId;
    private List<ReasoningAnswerDto> answers;
}
