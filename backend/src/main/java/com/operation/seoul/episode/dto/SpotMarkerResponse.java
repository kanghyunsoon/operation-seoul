package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpotMarkerResponse {
    private Long spotId;
    private String placeName;
    private String address;
    private Double latitude;
    private Double longitude;
    private String publicMarkerType;
    private String storyText;
    private boolean completed;
    private boolean visited;
    private boolean rewardClueCollected;
    private boolean canOpenPuzzle;
    private boolean canNavigate;
}
