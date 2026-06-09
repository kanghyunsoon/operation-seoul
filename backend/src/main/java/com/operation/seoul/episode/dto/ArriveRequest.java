package com.operation.seoul.episode.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class ArriveRequest {
    @DecimalMin(value = "-90.0", message = "위도 범위를 확인해 주세요.")
    @DecimalMax(value = "90.0", message = "위도 범위를 확인해 주세요.")
    private Double userLat;

    @DecimalMin(value = "-180.0", message = "경도 범위를 확인해 주세요.")
    @DecimalMax(value = "180.0", message = "경도 범위를 확인해 주세요.")
    private Double userLng;

    private Boolean devMode;
}
