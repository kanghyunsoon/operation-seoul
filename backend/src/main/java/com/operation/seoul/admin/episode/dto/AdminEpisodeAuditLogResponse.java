package com.operation.seoul.admin.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminEpisodeAuditLogResponse {
    private Long auditId;
    private Long episodeId;
    private String episodeTitle;
    private Long actorUserId;
    private String actorEmail;
    private String actorNickname;
    private String action;
    private String targetType;
    private Long targetId;
    private String summary;
    private String requestId;
    private LocalDateTime createdAt;
}
