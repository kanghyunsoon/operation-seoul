package com.operation.seoul.admin.episode.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiEpisodeDraftRequest {
    private String area;
    private String era;
    private String theme;
    private String targetAudience;
    private String playTime;

    // 선택된 장르
    private String selectedGenreId;
    private String selectedGenreName;

    // 기존 호환용: 나중에 제거 가능
    private List<String> finalAnswerKeywords;

    // 실서비스용
    private List<AnswerKeywordInput> finalAnswerKeywordItems;
    private List<GenreTemplateInput> genreCatalog;
    private MissionPolicyInput missionPolicy;
    private PuzzlePolicyInput puzzlePolicy;

    private List<PlaceInput> places;

    @Data
    public static class AnswerKeywordInput {
        private String slotId;      // CULPRIT, WEAPON, CASE_LOCATION 등
        private String label;       // 범인, 범행도구, 사건장소
        private String keyword;     // 화공, 붓, 후원
        private List<String> aliases;
    }

    @Data
    public static class GenreTemplateInput {
        private String genreId;
        private String genreName;
        private List<AnswerSlotInput> answerSlots;
        private List<String> recommendedPuzzleTypes;
        private List<String> forbiddenPatterns;
        private String finalQuestionTemplate;
    }

    @Data
    public static class AnswerSlotInput {
        private String slotId;
        private String label;
        private String description;
        private Integer minClueCount;
    }

    @Data
    public static class MissionPolicyInput {
        private Integer minMissionCount;
        private Integer maxMissionCount;
        private Integer missionCount;
        private Integer startCount;
        private Integer finalCount;
        private Integer minCluesPerAnswerSlot;
        private Double answerHintRatio;
        private Double destinationHintRatio;
        private Boolean allowDynamicMissionCount;
    }

    @Data
    public static class PuzzlePolicyInput {
        private Integer maxSamePuzzleTypeCount;
        private Boolean forbidPlaceNameTextExtraction;
        private Boolean forbidFinalKeywordAsPuzzleAnswer;
        private Boolean requireUniquePuzzleAnswer;
        private List<String> allowedPuzzleTypes;
        private List<String> blockedGenericAnswers;
    }

    @Data
    public static class PlaceInput {
        private String placeId;
        private String name;
        private String address;
        private Double latitude;
        private Double longitude;
        private String description;
        private List<String> visibleElements;
        private List<String> numbers;
        private List<String> keywords;
        private String adminMemo;
        private String role;
        private String publicMarkerType;
        private Double arrivalRadius;

        // 실서비스 검수용
        private String dataQuality;       // STRONG, NORMAL, WEAK, REVIEW_REQUIRED
        private List<String> usablePuzzleSources;
        private List<String> verificationNotes;
    }
}