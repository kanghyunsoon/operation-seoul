package com.operation.seoul.community.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RegionAnswerResponse {
    private Long id;
    private Long questionId;
    private Long userId;
    private String authorNickname;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean mine;
}
