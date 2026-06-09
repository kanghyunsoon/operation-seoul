package com.operation.seoul.episode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeductionAskRequest {
    @NotBlank(message = "추리 질문을 입력해 주세요.")
    @Size(max = 500, message = "추리 질문은 500자 이하여야 합니다.")
    private String question;
}
