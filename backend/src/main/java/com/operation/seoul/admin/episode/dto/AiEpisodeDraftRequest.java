package com.operation.seoul.admin.episode.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiEpisodeDraftRequest {
    private String area;
    private String era;
    private String theme;
    private String targetAudience;
    private String playTime;
    private List<PlaceInput> places;

    @Data
    public static class PlaceInput {
        private String name;
        private String address;
        private Double latitude;
        private Double longitude;
        private String description;
        private List<String> visibleElements;
        private List<String> numbers;
        private List<String> keywords;
        private String adminMemo;
        private String role;
        private String publicMarkerType;
        private Double arrivalRadius;
    }
}
