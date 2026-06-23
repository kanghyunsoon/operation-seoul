package com.operation.seoul.episode.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FinalDeductionSession {
    private Long id;
    private Long userId;
    private Long episodeId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer questionCount;
    private Integer hypothesisCount;
    private Integer finalGuessCount;
    private String status;
}
