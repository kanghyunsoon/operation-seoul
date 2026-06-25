package com.operation.seoul.auth.dto;

import com.operation.seoul.auth.domain.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private UserInfo user;

    public static AuthResponse of(String token, User user) {
        return AuthResponse.builder()
                .token(token)
                .user(UserInfo.of(user))
                .build();
    }

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String nickname;
        private String email;
        private String role;
        private String status;
        private String profileImageUrl;
        private String statusMessage;
        private boolean profilePublic;
        private boolean isAdmin;

        public static UserInfo of(User user) {
            return UserInfo.builder()
                    .id(user.getId())
                    .nickname(user.getNickname())
                    .email(user.getEmail())
                    .role(user.effectiveRole())
                    .status(user.effectiveStatus())
                    .profileImageUrl(user.getProfileImageUrl())
                    .statusMessage(user.getStatusMessage())
                    .profilePublic(user.isProfilePublic())
                    .isAdmin(user.isAdmin())
                    .build();
        }
    }
}
