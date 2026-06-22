package com.operation.seoul.admin.episode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEpisodePlanResponse {
    private String selectedGenreId;
    private String selectedGenreName;

    private List<AnswerSlotPlan> answerSlots;
    private List<AnswerKeyword> finalAnswerKeywords;
    private List<AnswerKeyword> finalAnswerKeywordItems;
    private FinalAnswers finalAnswers;

    private String finalQuestionGuide;
    private String rationale;
    private List<String> tourApiStoryAnchors;
    private boolean planReviewRequired;
    private String reviewReason;
    private boolean fieldVerificationRecommended;

    private List<String> rejectedGenreReasons;
    private List<String> validationWarnings;
    private List<String> nextSteps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerSlotPlan {
        private String slotId;
        private String label;
        private String description;
        private Integer minClueCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerKeyword {
        private String slotId;
        private String label;
        private String type;
        private String displayType;
        private String keyword;
        private String personName;
        private String personRole;
        private String role;
        private List<String> aliases;
        private Integer sourcePlaceOrder;
        private String sourceBasis;
        private String sourceType;
        private String sourcePlaceName;
        private String sourceText;
        private String difficulty;
        private String risk;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalAnswers {
        private String culprit;
        private String weapon;
        private String motive;
        private String method;

        // Legacy fields kept temporarily for older admin drafts and UI payloads.
        private String relatedPerson;
        private String coreClue;
        private String finalLocation;
    }
}
