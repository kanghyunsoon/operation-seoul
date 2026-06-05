package com.operation.seoul.episode.domain;

import lombok.Data;

@Data
public class Puzzle {
    private Long id;
    private Long missionSpotId;
    private String puzzleType;
    private String questionText;
    private String answer;
    private String answerFormat;
    private String rewardClue;
    private String rewardPayload;
    private String difficulty;
}
