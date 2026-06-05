package com.operation.seoul.episode.domain;

import lombok.Data;

@Data
public class PuzzleHint {
    private Long id;
    private Long puzzleId;
    private Integer hintLevel;
    private String hintText;
}
