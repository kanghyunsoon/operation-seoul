package com.operation.seoul.auth.controller;

import com.operation.seoul.auth.dto.AuthRequest;
import com.operation.seoul.auth.dto.AuthResponse;
import com.operation.seoul.auth.service.AuthService;
import com.operation.seoul.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody AuthRequest request) {
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("회원가입이 완료되었습니다. 로그인해 주세요."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("로그인되었습니다.", authService.login(request)));
    }
}