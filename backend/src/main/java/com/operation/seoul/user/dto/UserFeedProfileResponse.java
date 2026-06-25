package com.operation.seoul.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserFeedProfileResponse {
    private Long userId;
    private String nickname;
    private String statusMessage;
    private String profileImageUrl;
    private int postCount;
    private int achievedChallengeCount;
    private int followerCount;
    private int followingCount;
}
