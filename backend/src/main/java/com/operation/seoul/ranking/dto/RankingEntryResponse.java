package com.operation.seoul.ranking.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RankingEntryResponse {
    private Long episodeId;
    private String episodeTitle;
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private Integer score;
    private Integer wrongAnswerCount;
    private Integer deductionQuestionCount;
    private Integer finalGuessCount;
    private LocalDateTime clearedAt;
    private Integer rankNo;
}
