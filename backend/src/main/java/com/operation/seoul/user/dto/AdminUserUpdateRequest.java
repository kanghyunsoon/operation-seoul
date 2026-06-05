package com.operation.seoul.user.dto;

import lombok.Data;

@Data
public class AdminUserUpdateRequest {
    private String nickname;
    private String role;
    private String status;
    private String profileImageUrl;
}
