package com.operation.seoul.review.dto;

import lombok.Data;

@Data
public class EpisodeReviewRequest {
    private Integer rating;
    private Integer difficultyRating;
    private String content;
    private Boolean spoiler;
}