package com.operation.seoul.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegionQuestionRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String content;
}
