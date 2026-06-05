package com.operation.seoul.admin.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminEpisodeListResponse {
    private Long id;
    private String title;
    private String subtitle;
    private String genre;
    private String era;
    private String difficulty;
    private String status;
    private String finalAnswerType;
    private Integer spotCount;
    private Integer puzzleCount;
    private Integer suspectCount;
    private Integer evidenceCount;
    private Integer partnerRewardCount;
    private Long totalPlayers;
    private Long clearedPlayers;
}
