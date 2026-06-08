package com.operation.seoul.coaching.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachingReportResponse {
    private Long episodeId;
    private String episodeTitle;
    private String status;
    private Integer score;
    private Integer completedSpotCount;
    private Integer visitedSpotCount;
    private Integer hintUsedCount;
    private Integer wrongAnswerCount;
    private Integer deductionQuestionCount;
    private Integer finalGuessCount;
    private LocalDateTime startedAt;
    private LocalDateTime clearedAt;
    private String grade;
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
    private List<String> nextActions;
}
