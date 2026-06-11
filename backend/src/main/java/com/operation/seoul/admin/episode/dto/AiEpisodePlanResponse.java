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
public class AiEpisodePlanResponse {
    private String selectedGenre;
    private List<AnswerKeyword> finalAnswerKeywords;
    private String finalQuestionGuide;
    private String rationale;
    private List<String> nextSteps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerKeyword {
        private String label;
        private String keyword;
    }
}
