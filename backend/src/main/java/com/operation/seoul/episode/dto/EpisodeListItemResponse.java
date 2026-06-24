package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EpisodeListItemResponse {
    private Long id;
    private String title;
    private String subtitle;
    private String era;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private Boolean favorited;
    private String progressStatus;
    private Boolean cleared;
}
