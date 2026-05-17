package com.operation.seoul.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegionAnswerRequest {
    @NotBlank
    private String content;
}
