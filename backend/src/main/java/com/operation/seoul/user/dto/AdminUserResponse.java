package com.operation.seoul.user.dto;

import com.operation.seoul.auth.domain.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserResponse {
    private Long id;
    private String email;
    private String nickname;
    private String role;
    private String status;
    private String profileImageUrl;
    private boolean admin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminUserResponse of(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.effectiveRole())
                .status(user.effectiveStatus())
                .profileImageUrl(user.getProfileImageUrl())
                .admin(user.isAdmin())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
