package com.operation.seoul.episode.domain;

import lombok.Data;

@Data
public class Episode {
    private Long id;
    private String title;
    private String subtitle;
    private Long regionId;
    private String era;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private String fictionSynopsis;
    private String finalAnswerType;
    private String finalAnswer;
    private String finalAnswerAliases;
    private String finalQuestion;
    private String finalTruthSummary;
    private String actualHistorySummary;
    private String deductionSecretFacts;
    private String deductionForbiddenReveals;
    private Integer maxDeductionQuestions;
    private String recommendedPlayers;
    private String teamRoleGuide;
    private String noticeText;
    private String status;
}
