package com.operation.seoul.user.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.user.dto.AdminUserResponse;
import com.operation.seoul.user.dto.AdminUserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_USER", "ROLE_ADMIN");
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "SUSPENDED", "DELETED");

    private final UserRepository userRepository;

    public List<AdminUserResponse> getUsers(String keyword, String role, String status) {
        String normalizedKeyword = normalize(keyword);
        String normalizedRole = normalizeRole(role, false);
        String normalizedStatus = normalizeStatus(status, false);

        return userRepository.findAll().stream()
                .filter(user -> normalizedKeyword == null || matchesKeyword(user, normalizedKeyword))
                .filter(user -> normalizedRole == null || normalizedRole.equals(user.effectiveRole()))
                .filter(user -> normalizedStatus == null || normalizedStatus.equals(user.effectiveStatus()))
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(User::getId, Comparator.reverseOrder()))
                .map(AdminUserResponse::of)
                .toList();
    }

    public AdminUserResponse getUser(Long userId) {
        return AdminUserResponse.of(findUser(userId));
    }

    public AdminUserResponse updateUser(Long userId, AdminUserUpdateRequest request, Long actorId) {
        User user = findUser(userId);
        String nickname = request.getNickname() == null ? user.getNickname() : request.getNickname().trim();
        if (nickname == null || nickname.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "필수 입력값을 확인해 주세요.");
        }
        ensureUniqueNickname(userId, nickname);

        String role = normalizeRole(request.getRole() == null ? user.effectiveRole() : request.getRole(), true);
        String status = normalizeStatus(request.getStatus() == null ? user.effectiveStatus() : request.getStatus(), true);
        String profileImageUrl = request.getProfileImageUrl() == null ? user.getProfileImageUrl() : request.getProfileImageUrl().trim();
        // 관리자가 자기 권한을 내리거나 자기 계정을 정지하면 바로 관리 화면에서 잠기고, 관리자가 한 명이면 되돌릴 사람이 없다.
        if (userId.equals(actorId) && (!"ROLE_ADMIN".equals(role) || !"ACTIVE".equals(status))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_LOCKOUT", "자기 계정의 관리자 권한과 활성 상태는 바꿀 수 없습니다.");
        }

        userRepository.updateAdminFields(userId, nickname, role, status, blankToNull(profileImageUrl));
        return getUser(userId);
    }

    public void softDeleteUser(Long userId, Long actorId) {
        if (userId.equals(actorId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_LOCKOUT", "관리자 화면에서 자기 계정은 삭제할 수 없습니다.");
        }
        findUser(userId);
        userRepository.softDeleteById(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private void ensureUniqueNickname(Long currentUserId, String nickname) {
        boolean duplicated = userRepository.findAll().stream()
                .anyMatch(user -> nickname.equals(user.getNickname()) && !user.getId().equals(currentUserId));
        if (duplicated) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
        }
    }

    private boolean matchesKeyword(User user, String keyword) {
        return contains(user.getEmail(), keyword) || contains(user.getNickname(), keyword)
                || contains(user.effectiveRole(), keyword) || contains(user.effectiveStatus(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if ("ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role, boolean required) {
        if (role == null || role.trim().isBlank() || "ALL".equalsIgnoreCase(role)) {
            if (required) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "권한 값을 확인해 주세요.");
            }
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "권한 값을 확인해 주세요.");
        }
        return normalized;
    }

    private String normalizeStatus(String status, boolean required) {
        if (status == null || status.trim().isBlank() || "ALL".equalsIgnoreCase(status)) {
            if (required) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "회원 상태 값을 확인해 주세요.");
            }
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "회원 상태 값을 확인해 주세요.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
