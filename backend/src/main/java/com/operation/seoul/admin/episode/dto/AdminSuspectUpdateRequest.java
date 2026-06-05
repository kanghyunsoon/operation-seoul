package com.operation.seoul.admin.episode.dto;

import lombok.Data;

@Data
public class AdminSuspectUpdateRequest {
    private String displayName;
    private String alias;
    private String shortDescription;
    private String portraitImageUrl;
    private String relationToVictim;
    private String suspiciousPoint;
    private String alibiSummary;
    private Boolean unlockedByDefault;
    private Integer displayOrder;
}