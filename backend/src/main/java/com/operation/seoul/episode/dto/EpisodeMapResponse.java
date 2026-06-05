package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EpisodeMapResponse {
    private Long episodeId;
    private String title;
    private String progressStatus;
    private Integer hintUsedCount;
    private Integer wrongAnswerCount;
    private Integer deductionQuestionCount;
    private List<SpotMarkerResponse> spots;
}
