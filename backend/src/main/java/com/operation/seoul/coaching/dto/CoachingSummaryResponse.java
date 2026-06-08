package com.operation.seoul.coaching.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachingSummaryResponse {
    private Integer totalStarted;
    private Integer totalCleared;
    private Integer averageScore;
    private Integer totalHints;
    private Integer totalWrongAnswers;
    private Integer totalDeductionQuestions;
    private String playStyle;
    private List<String> globalAdvice;
    private List<CoachingReportResponse> recentReports;
}
