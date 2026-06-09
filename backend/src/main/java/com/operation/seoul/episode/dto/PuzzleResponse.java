package com.operation.seoul.episode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PuzzleResponse {
    private Long puzzleId;
    private Long spotId;
    private String puzzleType;
    private String questionText;
    private String answerFormat;
    private String difficulty;
    private List<String> hints;
    private Map<String, Object> interaction;
}
