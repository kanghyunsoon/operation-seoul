package com.operation.seoul.admin.episode.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminEpisodeUpdateRequest {
    private String title;
    private String subtitle;
    private String era;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private String fictionSynopsis;
    private String missionDescription;
    private String finalAnswerType;
    private String finalAnswer;
    private String finalAnswerAliases;
    private List<FinalAnswerKeywordItem> finalAnswerKeywordItems;
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

    @Data
    public static class FinalAnswerKeywordItem {
        private String type;
        private String displayType;
        private String value;
        private List<String> aliases;
    }
}
