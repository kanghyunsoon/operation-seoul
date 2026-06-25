package com.operation.seoul.playeranalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayMbtiDto {
    private String dimension;
    private String leftLabel;
    private String rightLabel;
    private int leftPercent;
    private int rightPercent;
}
