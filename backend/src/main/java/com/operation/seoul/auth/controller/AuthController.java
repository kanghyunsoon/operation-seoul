package com.operation.seoul.auth.controller;

import com.operation.seoul.auth.dto.AuthRequest;
import com.operation.seoul.auth.dto.AuthResponse;
import com.operation.seoul.auth.dto.GoogleLoginRequest;
import com.operation.seoul.auth.dto.KakaoLoginRequest;
import com.operation.seoul.auth.dto.OAuthConfigResponse;
import com.operation.seoul.auth.service.AuthService;
import com.operation.seoul.auth.service.OAuthService;
import com.operation.seoul.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final OAuthService oAuthService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody AuthRequest request) {
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("회원가입이 완료되었습니다. 로그인해 주세요."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("로그인되었습니다.", authService.login(request)));
    }

    @GetMapping("/oauth/config")
    public ResponseEntity<ApiResponse<OAuthConfigResponse>> oauthConfig() {
        return ResponseEntity.ok(ApiResponse.ok("OAuth 설정입니다.", oAuthService.config()));
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Google 로그인되었습니다.",
                oAuthService.loginWithGoogle(request.getCredential())));
    }

    @PostMapping("/oauth/kakao")
    public ResponseEntity<ApiResponse<AuthResponse>> kakaoLogin(@RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Kakao 로그인되었습니다.",
                oAuthService.loginWithKakao(request.getCode(), request.getRedirectUri())));
    }
}
