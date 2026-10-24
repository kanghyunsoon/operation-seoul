package com.operation.seoul.user.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.user.dto.AdminUserResponse;
import com.operation.seoul.user.dto.AdminUserUpdateRequest;
import com.operation.seoul.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> getUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok("회원 목록입니다.", adminUserService.getUsers(keyword, role, status)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("회원 상세 정보입니다.", adminUserService.getUser(userId)));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @PathVariable Long userId,
            @RequestBody AdminUserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("회원 정보가 수정되었습니다.", adminUserService.updateUser(userId, request, currentUserResolver.requireCurrentUser().getId())));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        adminUserService.softDeleteUser(userId, currentUserResolver.requireCurrentUser().getId());
        return ResponseEntity.ok(ApiResponse.ok("회원 상태가 변경되었습니다."));
    }
}
