package com.operation.seoul.auth.security;

import com.operation.seoul.auth.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    /**
     * JWT 인증 정보가 있으면 인증된 사용자 id를 우선 사용하고,
     * 개발 중 호환을 위해 query/body에 전달된 fallback id를 보조로 사용합니다.
     */
    public Long resolveUserId(Long fallbackUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return fallbackUserId != null ? fallbackUserId : 1L;
    }

    /**
     * 인증된 사용자 권한을 기준으로 관리자 여부를 판단합니다.
     * 로그인 전 개발용 호출이 남아 있어 fallback 값을 허용하지만, 운영 판단은 토큰 기준입니다.
     */
    public boolean resolveIsAdmin(boolean fallbackIsAdmin) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.isAdmin();
        }
        return fallbackIsAdmin;
    }
}
