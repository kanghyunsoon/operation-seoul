package com.operation.seoul.community.dto;

import lombok.Data;

@Data
public class RegionReviewSummary {
    private Long regionId;
    private Double averageRating;
    private Integer reviewCount;
}
