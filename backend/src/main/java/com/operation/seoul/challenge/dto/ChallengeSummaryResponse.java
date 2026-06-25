package com.operation.seoul.challenge.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChallengeSummaryResponse {
    private List<ChallengeGoal> activeGoals;
    private List<ChallengeGoal> completedGoals;
    private int completedCount;

    @Data
    @Builder
    public static class ChallengeGoal {
        private String type;
        private String title;
        private String description;
        private int currentValue;
        private int targetValue;
        private boolean completed;
    }
}
