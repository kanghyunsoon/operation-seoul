package com.operation.seoul.admin.episode.dto;

import lombok.Data;

@Data
public class AdminPartnerRewardUpdateRequest {
    private String title;
    private String description;
    private String rewardType;
    private String partnerName;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private String status;
}