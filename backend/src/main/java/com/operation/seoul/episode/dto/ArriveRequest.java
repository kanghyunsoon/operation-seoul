package com.operation.seoul.episode.dto;

import lombok.Data;

@Data
public class ArriveRequest {
    private Double userLat;
    private Double userLng;
    private Boolean devMode;
}
