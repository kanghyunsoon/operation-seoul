package com.operation.seoul.casefile.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EpisodePartnerReward {
    private Long id;
    private Long episodeId;
    private String title;
    private String description;
    private String rewardType;
    private String partnerName;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private String status;
    private LocalDateTime createdAt;
}