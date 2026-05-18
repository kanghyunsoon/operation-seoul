package com.operation.seoul.community.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RegionReviewResponse {
    private Long id;
    private Long regionId;
    private Long userId;
    private String authorNickname;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long clearElapsedSeconds;
    private Integer clearScore;
    private boolean mine;
    private int likeCount;
    private boolean liked;
}
