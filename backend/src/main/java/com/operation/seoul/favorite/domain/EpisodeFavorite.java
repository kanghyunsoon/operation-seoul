package com.operation.seoul.favorite.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EpisodeFavorite {
    private Long id;
    private Long userId;
    private Long episodeId;
    private LocalDateTime createdAt;
}
