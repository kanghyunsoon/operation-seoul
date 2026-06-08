package com.operation.seoul.plan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPlanResponse {
    private Long id;
    private Long episodeId;
    private String episodeTitle;
    private String episodeSubtitle;
    private String era;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private LocalDateTime plannedAt;
    private String memo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
