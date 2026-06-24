package com.operation.seoul.review.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EpisodeReviewCommentResponse {
    private Long id;
    private Long reviewId;
    private Long userId;
    private String authorNickname;
    private String content;
    private Boolean spoiler;
    private Boolean mine;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
