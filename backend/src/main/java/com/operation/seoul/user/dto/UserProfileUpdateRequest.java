package com.operation.seoul.user.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String nickname;
    private String profileImageUrl;
    private String statusMessage;
    private Boolean profilePublic;
}
