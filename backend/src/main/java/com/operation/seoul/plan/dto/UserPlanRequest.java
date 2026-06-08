package com.operation.seoul.plan.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserPlanRequest {
    private Long episodeId;
    private LocalDateTime plannedAt;
    private String memo;
    private String status;
}
