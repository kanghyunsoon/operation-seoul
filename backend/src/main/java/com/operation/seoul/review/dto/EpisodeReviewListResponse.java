package com.operation.seoul.review.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EpisodeReviewListResponse {
    private List<EpisodeReviewResponse> reviews;
    private Double averageRating;
    private Double averageDifficultyRating;
    private Integer reviewCount;
    private Boolean canReview;
    private Long myReviewId;
    private String message;
}