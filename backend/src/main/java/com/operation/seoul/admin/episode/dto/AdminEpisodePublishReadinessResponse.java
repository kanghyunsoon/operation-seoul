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
public class AdminEpisodePublishReadinessResponse {
    private boolean ready;
    private String status;
    private String message;
    private Summary summary;
    private List<String> blockingIssues;
    private List<String> checklist;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int spotCount;
        private long startCount;
        private long answerHintCount;
        private long destinationHintCount;
        private long finalPlaceCount;
        private long finalCandidateCount;
        private int puzzleCount;
        private int suspectCount;
        private int evidenceCount;
    }
}
