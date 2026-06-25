package com.operation.seoul.playeranalysis.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlayerAnalysis {
    private Long id;
    private Long userId;
    private Long missionId;
    private String playerType;
    private String summary;
    private String strength;
    private String weakness;
    private String recommendation;
    private LocalDateTime createdAt;
}
