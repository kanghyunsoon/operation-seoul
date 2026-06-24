package com.operation.seoul.review.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EpisodeReviewComment {
    private Long id;
    private Long reviewId;
    private Long userId;
    private String content;
    private Boolean spoiler;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String authorNickname;
}
