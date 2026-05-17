package com.operation.seoul.community.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RegionReviewListResponse {
    private List<RegionReviewResponse> reviews;
    private double averageRating;
    private int reviewCount;
    private boolean canReview;
    private Long myReviewId;
}
