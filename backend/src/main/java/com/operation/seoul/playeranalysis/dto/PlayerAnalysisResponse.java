package com.operation.seoul.playeranalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerAnalysisResponse {
    private String playerType;
    private String summary;
    private String strength;
    private String weakness;
    private String recommendation;
    private List<PlayMbtiDto> playMbti;
}
