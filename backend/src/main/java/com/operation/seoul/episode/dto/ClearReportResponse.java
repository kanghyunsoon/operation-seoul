package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ClearReportResponse {
    private Long episodeId;
    private String title;
    private String finalQuestion;
    private String finalAnswerType;
    private Integer score;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime clearedAt;
    private Long elapsedSeconds;
    private String finalTruthSummary;
    private String actualHistorySummary;
    private Integer visitedSpotCount;
    private Integer completedSpotCount;
    private Integer totalSpotCount;
    private Integer answerClueCount;
    private Integer destinationClueCount;
    private Integer storyClueCount;
    private Integer hintUsedCount;
    private Integer deductionQuestionCount;
    private Integer wrongAnswerCount;
    private Integer finalGuessCount;
    private List<String> culpritClues;
    private List<String> weaponClues;
    private List<String> motiveClues;
    private List<String> methodClues;

    // Legacy fields kept for existing clients and saved progress.
    private List<String> answerClues;
    private List<String> destinationClues;
    private List<String> storyClues;
    private List<Long> unlockedSuspectIds;
    private List<Long> unlockedEvidenceIds;
    private String finalArrivedSpotName;
    private boolean canReview;
}
