package com.operation.seoul.episode.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserEpisodeProgress {
    private Long id;
    private Long userId;
    private Long episodeId;
    private String visitedSpotIds;
    private String completedSpotIds;
    private String collectedAnswerClues;
    private String collectedDestinationClues;
    private String collectedStoryClues;
    private Long finalArrivedSpotId;
    private Integer hintUsedCount;
    private Integer wrongAnswerCount;
    private Integer deductionQuestionCount;
    private Integer hypothesisCount;
    private Integer finalGuessCount;
    private Integer clearTimePenaltySeconds;
    private Integer score;
    private LocalDateTime startedAt;
    private LocalDateTime lastPlayedAt;
    private LocalDateTime clearedAt;
    private String status;
    private String unlockedSuspectIds;
    private String clearedSuspectIds;
    private String unlockedEvidenceIds;
}
