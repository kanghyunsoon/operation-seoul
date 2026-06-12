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
        private String selectedGenre;
        private List<String> finalAnswerKeywords;
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

        // 추가
        private String puzzleAnswerSource;   // NUMBER, VISIBLE_ELEMENT, KEYWORD, ADMIN_MEMO, FICTION_SAFE
        private String puzzleAnswerRisk;     // OK, GENERIC, PLACE_NAME_RISK, FINAL_KEYWORD_RISK

        private String rewardClue;
        private String rewardClueSlotId;     // WEAPON, CULPRIT, CASE_LOCATION 등
        private String rewardClueLabel;      // 범행도구 단서, 범인 단서 등
        private List<String> supportsKeywordSlots;

        private List<String> hints;
        private String groundRule;
        private String verificationLevel;    // AUTO_OK, ADMIN_REVIEW, FIELD_REQUIRED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspectDraft {
        private String alias;
        private String displayName;
        private String portraitImageUrl;
        private String imagePrompt;
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
        private String imagePrompt;
        private String textSummary;
        private Integer sourceMissionOrder;
    }
}
