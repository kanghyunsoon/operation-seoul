package com.operation.seoul.casefile.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CaseEvidence {
    private Long id;
    private Long episodeId;
    private String title;
    private String type;
    private String imageUrl;
    private String imagePrompt;
    private String textSummary;
    private Long sourceSpotId;
    private Long relatedSuspectId;
    private String relatedClueType;
    private Boolean unlockedByDefault;
    private Integer displayOrder;
    private LocalDateTime createdAt;
}
