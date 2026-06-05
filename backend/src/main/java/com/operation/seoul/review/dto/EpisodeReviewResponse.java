package com.operation.seoul.review.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EpisodeReviewResponse {
    private Long id;
    private Long episodeId;
    private String episodeTitle;
    private Long userId;
    private String authorNickname;
    private Integer rating;
    private Integer difficultyRating;
    private String content;
    private Boolean spoiler;
    private String status;
    private boolean mine;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}