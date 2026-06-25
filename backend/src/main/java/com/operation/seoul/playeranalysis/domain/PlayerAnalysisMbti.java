package com.operation.seoul.playeranalysis.domain;

import lombok.Data;

@Data
public class PlayerAnalysisMbti {
    private Long id;
    private Long analysisId;
    private String dimension;
    private String leftLabel;
    private String rightLabel;
    private Integer leftPercent;
    private Integer rightPercent;
}
