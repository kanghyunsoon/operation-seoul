package com.operation.seoul.admin.episode.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEpisodeDraftResponse {
    private String generatorType;
    private String message;
    private EpisodeDraft draft;
    private List<String> validationWarnings;
    private List<String> nextSteps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EpisodeDraft {
        private String episodeTitle;
        private String subtitle;
        private String genre;
        private String era;
        private String fictionSynopsis;
        private String finalAnswerType;
        private String finalAnswer;
        private List<String> finalAnswerAliases;
        private String finalQuestion;
        private String finalTruthSummary;
        private String actualHistorySummary;
        private List<String> deductionSecretFacts;
        private List<String> deductionForbiddenReveals;
        private Integer maxDeductionQuestions;
        private List<MissionDraft> missions;
        private List<SuspectDraft> suspects;
        private List<EvidenceDraft> evidences;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissionDraft {
        private Integer order;
        private String placeName;
        private String address;
        private Double latitude;
        private Double longitude;
        private String markerType;
        private String publicMarkerType;
        private String clueRole;
        private Boolean finalPlace;
        private String storyText;
        private Double arrivalRadius;
        private String puzzleType;
        private String questionText;
        private String answer;
        private String answerFormat;
        private String rewardClue;
        private List<String> hints;
        private String groundRule;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspectDraft {
        private String alias;
        private String displayName;
        private String portraitImageUrl;
        private String shortDescription;
        private String relationToVictim;
        private String suspiciousPoint;
        private String alibiSummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceDraft {
        private String title;
        private String type;
        private String imageUrl;
        private String textSummary;
        private Integer sourceMissionOrder;
    }
}
