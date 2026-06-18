package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PuzzleSubmitResponse {
    private boolean correct;
    private String rewardClue;
    private boolean caseFileUpdated;
    private List<String> unlockedRewardTypes;
    private List<Long> unlockedEvidenceIds;
    private List<Long> unlockedSuspectIds;
    private List<Long> updatedSuspectIds;
    private List<Long> unlockedPhotoIds;
    private List<Long> unlockedMemoIds;
    private List<UnlockedCaseFileItem> newlyUnlockedItems;
    private ClueBoardResponse clueBoard;
    private Map<String, Object> retryInteraction;
    private String message;

    @Data
    @Builder
    public static class UnlockedCaseFileItem {
        private String rewardType;
        private String itemType;
        private Long targetId;
    }
}
