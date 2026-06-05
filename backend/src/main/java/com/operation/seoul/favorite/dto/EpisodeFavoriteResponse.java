package com.operation.seoul.favorite.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeFavoriteResponse {
    private Long favoriteId;
    private Long episodeId;
    private String title;
    private String subtitle;
    private String era;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private Boolean favorited;
    private LocalDateTime createdAt;
}
