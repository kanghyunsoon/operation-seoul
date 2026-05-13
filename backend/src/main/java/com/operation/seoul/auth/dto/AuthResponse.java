package com.operation.seoul.auth.dto;

import com.operation.seoul.auth.domain.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    /** 프론트가 Authorization 헤더에 담아 보낼 Bearer 토큰입니다. */
    private String token;

    /** 화면 표시와 권한 분기에 필요한 최소 사용자 정보입니다. */
    private UserInfo user;

    /** 엔티티 전체를 노출하지 않도록 응답 전용 DTO로 변환합니다. */
    public static AuthResponse of(String token, User user) {
        return AuthResponse.builder()
                .token(token)
                .user(UserInfo.of(user))
                .build();
    }

    @Data
    @Builder
    public static class UserInfo {
        /** 현재 사용자 id입니다. 기존 일부 API의 fallback userId와 호환됩니다. */
        private Long id;
        /** 홈 화면 헤더 등에 표시할 코드네임입니다. */
        private String nickname;
        /** 사용자가 로그인한 이메일입니다. */
        private String email;
        /** 프론트에서 관리자 메뉴 표시 여부를 결정하는 값입니다. */
        private boolean isAdmin;

        /** User 엔티티에서 민감한 password를 제외한 값만 복사합니다. */
        public static UserInfo of(User user) {
            return UserInfo.builder()
                    .id(user.getId())
                    .nickname(user.getNickname())
                    .email(user.getEmail())
                    .isAdmin(user.isAdmin())
                    .build();
        }
    }
}
