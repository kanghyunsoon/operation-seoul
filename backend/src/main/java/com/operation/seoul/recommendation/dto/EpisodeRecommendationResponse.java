package com.operation.seoul.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeRecommendationResponse {
    private Long episodeId;
    private String title;
    private String subtitle;
    private String era;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private Boolean favorited;
    private Boolean planned;
    private Boolean cleared;
    private Integer score;
    private String reason;
}
