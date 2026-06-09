package com.operation.seoul.episode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FinalAnswerRequest {
    @Positive(message = "추리 세션 ID를 확인해 주세요.")
    private Long sessionId;

    @NotBlank(message = "최종 답안을 입력해 주세요.")
    @Size(max = 200, message = "최종 답안은 200자 이하여야 합니다.")
    private String finalAnswer;
}
