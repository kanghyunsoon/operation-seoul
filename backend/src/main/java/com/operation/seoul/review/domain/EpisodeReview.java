package com.operation.seoul.review.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EpisodeReview {
    private Long id;
    private Long episodeId;
    private Long userId;
    private Integer rating;
    private Integer difficultyRating;
    private String content;
    private Boolean spoiler;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String authorNickname;
    private String episodeTitle;
}