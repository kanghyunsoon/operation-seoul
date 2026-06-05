package com.operation.seoul.admin.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminEpisodeDetailResponse {
    private Long id;
    private String title;
    private String subtitle;
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
    private ProgressStats progressStats;
    private List<Spot> spots;
    private List<Suspect> suspects;
    private List<Evidence> evidences;
    private List<PartnerReward> partnerRewards;

    @Data
    @Builder
    public static class ProgressStats {
        private Long totalPlayers;
        private Long inProgressPlayers;
        private Long clearedPlayers;
    }

    @Data
    @Builder
    public static class Spot {
        private Long spotId;
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
        private Puzzle puzzle;
    }

    @Data
    @Builder
    public static class Puzzle {
        private Long puzzleId;
        private String puzzleType;
        private String questionText;
        private String answer;
        private String answerFormat;
        private String rewardClue;
        private String rewardPayload;
        private String difficulty;
        private List<Hint> hints;
    }

    @Data
    @Builder
    public static class Hint {
        private Integer hintLevel;
        private String hintText;
    }

    @Data
    @Builder
    public static class Suspect {
        private Long suspectId;
        private String displayName;
        private String alias;
        private String shortDescription;
        private String portraitImageUrl;
        private String relationToVictim;
        private String suspiciousPoint;
        private String alibiSummary;
        private Boolean unlockedByDefault;
        private Integer displayOrder;
    }

    @Data
    @Builder
    public static class Evidence {
        private Long evidenceId;
        private String title;
        private String type;
        private String imageUrl;
        private String textSummary;
        private Long sourceSpotId;
        private Long relatedSuspectId;
        private String relatedClueType;
        private Boolean unlockedByDefault;
        private Integer displayOrder;
    }

    @Data
    @Builder
    public static class PartnerReward {
        private Long rewardId;
        private String title;
        private String description;
        private String rewardType;
        private String partnerName;
        private String locationName;
        private Double latitude;
        private Double longitude;
        private String status;
    }
}
