package com.operation.seoul.community.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RegionQuestionResponse {
    private Long id;
    private Long regionId;
    private Long userId;
    private String authorNickname;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean mine;
    private int likeCount;
    private boolean liked;
    private List<RegionAnswerResponse> answers;
}
