package com.operation.seoul.location.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegionCardResponse {
    private Long id;
    private String name;
    private String description;
    private Long finalMissionId;
    private boolean cleared;
    private String answerKeyword;
    private Integer score;
    private Long elapsedSeconds;
    private Double routeDistanceMeters;
}
