package com.operation.seoul.auth.security;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.global.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    public Long resolveUserId(Long ignoredFallbackUserId) {
        return requireCurrentUser().getId();
    }

    public boolean resolveIsAdmin(boolean ignoredFallbackIsAdmin) {
        return requireCurrentUser().isAdmin();
    }

    public User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            if (!user.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "이용이 제한된 계정입니다.");
            }
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "다시 로그인해 주세요.");
    }
}