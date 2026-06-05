package com.operation.seoul.user.controller;

import com.operation.seoul.auth.dto.AuthResponse;
import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.user.dto.PasswordChangeRequest;
import com.operation.seoul.user.dto.UserProfileUpdateRequest;
import com.operation.seoul.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {
    private final CurrentUserResolver currentUserResolver;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> me() {
        return ResponseEntity.ok(ApiResponse.ok("현재 사용자 정보입니다.", userService.toUserInfo(currentUserResolver.requireCurrentUser())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> updateMe(@RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("회원 정보가 수정되었습니다.", userService.updateProfile(currentUserResolver.requireCurrentUser(), request)));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody PasswordChangeRequest request) {
        userService.changePassword(currentUserResolver.requireCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 변경되었습니다. 다시 로그인해 주세요."));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteMe() {
        userService.softDelete(currentUserResolver.requireCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("회원 탈퇴가 완료되었습니다."));
    }
}