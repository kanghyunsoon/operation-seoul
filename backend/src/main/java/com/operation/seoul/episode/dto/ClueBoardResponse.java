package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClueBoardResponse {
    private Long episodeId;
    private List<String> answerClues;
    private List<String> destinationClues;
    private List<String> storyClues;
    private List<Long> visitedSpotIds;
    private List<Long> completedSpotIds;
    private List<Long> unlockedSuspectIds;
    private List<Long> clearedSuspectIds;
    private List<Long> unlockedEvidenceIds;
    private Integer answerClueCount;
    private Integer destinationClueCount;
    private Integer storyClueCount;
}
