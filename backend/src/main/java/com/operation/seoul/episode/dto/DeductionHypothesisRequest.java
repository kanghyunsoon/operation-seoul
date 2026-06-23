package com.operation.seoul.episode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeductionHypothesisRequest {
    @NotBlank(message = "가설을 입력해 주세요.")
    @Size(max = 500, message = "가설은 500자 이하여야 합니다.")
    private String hypothesis;
}
