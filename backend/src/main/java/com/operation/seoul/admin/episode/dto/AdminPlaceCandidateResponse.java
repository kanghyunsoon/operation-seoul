package com.operation.seoul.admin.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminPlaceCandidateResponse {
    private String title;
    private String address;
    private Double latitude;
    private Double longitude;
    private String areaCode;
    private String source;
    private String description;
    private String contentId;
}