package com.operation.seoul.auth.security;

import com.operation.seoul.auth.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    public Long resolveUserId(Long fallbackUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return fallbackUserId != null ? fallbackUserId : 1L;
    }

    public boolean resolveIsAdmin(boolean fallbackIsAdmin) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.isAdmin();
        }
        return fallbackIsAdmin;
    }
}
