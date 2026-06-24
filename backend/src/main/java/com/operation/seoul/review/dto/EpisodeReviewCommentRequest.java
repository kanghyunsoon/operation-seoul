package com.operation.seoul.review.dto;

import lombok.Data;

@Data
public class EpisodeReviewCommentRequest {
    private String content;
    private Boolean spoiler;
}
