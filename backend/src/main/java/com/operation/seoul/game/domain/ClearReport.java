package com.operation.seoul.game.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ClearReport {
    private Long id;
    private Long userId;
    private Long missionId;
    private String report;
    private String clueExplanationsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
