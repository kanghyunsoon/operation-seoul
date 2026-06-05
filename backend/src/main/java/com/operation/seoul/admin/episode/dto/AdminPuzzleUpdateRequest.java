package com.operation.seoul.admin.episode.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminPuzzleUpdateRequest {
    private String puzzleType;
    private String questionText;
    private String answer;
    private String answerFormat;
    private String rewardClue;
    private String rewardPayload;
    private String difficulty;
    private List<String> hints;
}