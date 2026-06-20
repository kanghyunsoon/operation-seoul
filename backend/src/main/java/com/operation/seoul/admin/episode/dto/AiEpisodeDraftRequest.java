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
    private FinalAnswersInput finalAnswers;
    private List<GenreTemplateInput> genreCatalog;
    private MissionPolicyInput missionPolicy;
    private PuzzlePolicyInput puzzlePolicy;

    private List<PlaceInput> places;
    private List<MissionInput> missions;
    private PlaceInput finalSpot;

    @Data
    public static class AnswerKeywordInput {
        private String slotId;      // CULPRIT, WEAPON, MOTIVE, METHOD
        private String label;       // 범인, 흉기, 동기, 방법
        private String type;        // CULPRIT, WEAPON, MOTIVE, METHOD 또는 PERSON/TEXT 호환값
        private String displayType; // 범인, 흉기, 동기, 방법
        private String keyword;     // 최종 정답 값
        private String personName;  // PERSON 타입의 가상 인물 이름
        private String personRole;  // PERSON 타입의 직업/역할
        private String role;        // personRole 호환 필드
        private List<String> aliases;

        private Integer sourcePlaceOrder;
        private String sourceBasis;
        private String sourceType;
        private String sourcePlaceName;
        private String sourceText;
        private String risk;
    }

    @Data
    public static class FinalAnswersInput {
        private String culprit;
        private String weapon;
        private String motive;
        private String method;

        // Legacy fields kept temporarily for older admin drafts and UI payloads.
        private String relatedPerson;
        private String coreClue;
        private String finalLocation;
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
        private List<String> externalResearchNotes;
        private List<String> referenceUrls;
        private String researchSourceSummary;
    }

    @Data
    public static class MissionInput {
        private Integer order;
        private String placeName;
        private String markerType;
        private String publicMarkerType;
        private String clueRole;
        private Boolean finalPlace;
        private String targetKeywordType;
        private String targetKeywordDisplayType;
        private String unlockCondition;
        private List<String> supportsKeywordSlots;
    }
}
