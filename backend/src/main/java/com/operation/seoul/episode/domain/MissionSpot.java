package com.operation.seoul.episode.domain;

import lombok.Data;

@Data
public class MissionSpot {
    private Long id;
    private Long episodeId;
    private String placeName;
    private String address;
    private Double latitude;
    private Double longitude;
    private String markerType;
    private String clueRole;
    private String publicMarkerType;
    private String storyText;
    private Double arrivalRadius;
    private Boolean finalPlace;
    private Boolean fieldVerified;
    private String fieldVerificationNote;
}
