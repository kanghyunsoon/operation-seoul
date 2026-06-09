package com.operation.seoul.episode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PuzzleSubmitRequest {
    @NotBlank(message = "퍼즐 답안을 입력해 주세요.")
    @Size(max = 500, message = "퍼즐 답안은 500자 이하여야 합니다.")
    private String answer;
}
