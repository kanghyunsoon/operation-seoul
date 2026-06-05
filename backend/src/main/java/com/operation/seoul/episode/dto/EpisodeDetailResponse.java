package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EpisodeDetailResponse {
    private Long id;
    private String title;
    private String subtitle;
    private String era;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private String fictionSynopsis;
    private String finalAnswerType;
    private String finalQuestion;
    private String progressStatus;
    private Boolean favorited;
}
