package com.operation.seoul.admin.episode.dto;

import lombok.Data;

@Data
public class AdminEvidenceUpdateRequest {
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
}
