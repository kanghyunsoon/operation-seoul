package com.operation.seoul.challenge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeResponse {
    private Long id;
    private String title;
    private String description;
    private String targetType;
    private Integer targetCount;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean joined;
    private String entryStatus;
    private Integer progressCount;
    private Boolean completed;
    private LocalDateTime joinedAt;
    private LocalDateTime completedAt;
}
