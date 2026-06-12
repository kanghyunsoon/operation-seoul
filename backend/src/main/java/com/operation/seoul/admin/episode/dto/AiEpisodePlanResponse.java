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

    private String finalQuestionGuide;
    private String rationale;
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
        private String keyword;
        private List<String> aliases;
        private Integer sourcePlaceOrder;
        private String sourceBasis;
        private String sourceType;
        private String sourcePlaceName;
        private String sourceText;
        private String difficulty;
        private String risk;
    }
}
