package com.operation.seoul.casefile.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CaseSuspect {
    private Long id;
    private Long episodeId;
    private String displayName;
    private String alias;
    private String shortDescription;
    private String portraitImageUrl;
    private String imagePrompt;
    private String relationToVictim;
    private String suspiciousPoint;
    private String alibiSummary;
    private Boolean unlockedByDefault;
    private Integer displayOrder;
    private LocalDateTime createdAt;
}
