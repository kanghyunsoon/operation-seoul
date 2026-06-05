package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArriveResponse {
    private boolean arrived;
    private double distance;
    private boolean canOpenPuzzle;
    private boolean isActualFinalArrived;
    private boolean canStartDeduction;
    private String message;
}
