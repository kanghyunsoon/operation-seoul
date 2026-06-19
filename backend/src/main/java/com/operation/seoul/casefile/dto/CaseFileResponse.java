package com.operation.seoul.casefile.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CaseFileResponse {
    private Long episodeId;
    private String title;
    private String subtitle;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private String recommendedPlayers;
    private String startRegion;
    private String progressStatus;
    private String finalQuestion;
    private Overview overview;
    private List<Suspect> suspects;
    private List<Evidence> evidences;
    private ClueSummary clueSummary;
    private ProgressSummary progressSummary;
    private List<String> notices;
    private String teamRoleGuide;
    private List<PartnerReward> partnerRewards;
    private AnswerLog answerLog;

    @Data
    @Builder
    public static class Overview {
        private String briefingTitle;
        private String summary;
        private String lockedSummary;
        private String detailedSummary;
        private String goal;
        private String fictionSynopsis;
        private String missionDescription;
        private boolean storyUnlocked;
        private List<String> unlockedStoryClues;
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
        private boolean unlocked;
        private boolean cleared;
        private int relatedClueCount;
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
        private List<Long> relatedSuspectIds;
        private String relatedClueType;
        private boolean unlocked;
    }

    @Data
    @Builder
    public static class ClueSummary {
        private List<String> culpritClues;
        private List<String> weaponClues;
        private List<String> motiveClues;
        private List<String> methodClues;

        // Legacy fields kept for existing clients and saved progress.
        private List<String> relatedPersonClues;
        private List<String> coreClues;
        private List<String> answerClues;
        private List<String> destinationClues;
        private List<String> storyClues;
    }

    @Data
    @Builder
    public static class ProgressSummary {
        private int visitedSpotCount;
        private int completedSpotCount;
        private int totalSpotCount;
        private int unlockedEvidenceCount;
        private int totalEvidenceCount;
        private int unlockedSuspectCount;
        private int totalSuspectCount;
        private int hintUsedCount;
        private int wrongAnswerCount;
        private int deductionQuestionCount;
        private Integer score;
    }

    @Data
    @Builder
    public static class PartnerReward {
        private String title;
        private String description;
        private String rewardType;
        private String partnerName;
        private String locationName;
        private String status;
    }

    @Data
    @Builder
    public static class AnswerLog {
        private List<Long> visitedSpotIds;
        private List<Long> completedSpotIds;
        private Long finalArrivedSpotId;
        private String startedAt;
        private String lastPlayedAt;
        private String clearedAt;
    }
}
