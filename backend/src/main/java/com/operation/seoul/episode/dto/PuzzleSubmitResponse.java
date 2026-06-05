package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PuzzleSubmitResponse {
    private boolean correct;
    private String rewardClue;
    private boolean caseFileUpdated;
    private List<String> unlockedRewardTypes;
    private List<Long> unlockedEvidenceIds;
    private List<Long> unlockedSuspectIds;
    private ClueBoardResponse clueBoard;
    private String message;
}
