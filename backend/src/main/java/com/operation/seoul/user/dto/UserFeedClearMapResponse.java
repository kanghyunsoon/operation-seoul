package com.operation.seoul.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFeedClearMapResponse {
    private Long episodeId;
    private String title;
    private String subtitle;
    private String regionName;
    private String era;
    private String genre;
    private String difficulty;
    private String estimatedTime;
    private String estimatedDistance;
    private Integer score;
    private LocalDateTime clearedAt;
}
