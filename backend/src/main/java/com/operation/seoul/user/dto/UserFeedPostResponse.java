package com.operation.seoul.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFeedPostResponse {
    private Long id;
    private Long regionId;
    private String regionName;
    private String title;
    private String content;
    private int likeCount;
    private int commentCount;
    private LocalDateTime createdAt;
}
