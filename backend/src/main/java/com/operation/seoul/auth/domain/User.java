package com.operation.seoul.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private Long id;
    private String email;
    private String password;
    private String nickname;
    private String role;
    private String profileImageUrl;
    private String statusMessage;
    @Builder.Default
    private boolean profilePublic = true;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private boolean admin = false;

    public boolean isAdmin() {
        return admin || "ROLE_ADMIN".equals(role);
    }

    public boolean isActive() {
        return status == null || "ACTIVE".equals(status);
    }

    public String effectiveRole() {
        if (role != null && !role.isBlank()) {
            return role;
        }
        return admin ? "ROLE_ADMIN" : "ROLE_USER";
    }

    public String effectiveStatus() {
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }
}
