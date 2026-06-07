package com.operation.seoul.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserFollowResponse {
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private Boolean following;
    private Integer followerCount;
    private Integer followingCount;
}
