package com.operation.seoul.ranking.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerRankingResponse {
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private Integer totalScore;
    private Integer clearCount;
    private Integer wrongAnswerCount;
    private Integer deductionQuestionCount;
    private Integer finalGuessCount;
    private Integer achievedChallengeCount;
    private Integer rankNo;
}
