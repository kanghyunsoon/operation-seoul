package com.operation.seoul.auth.controller;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.dto.AuthRequest;
import com.operation.seoul.auth.dto.AuthResponse;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 신규 요원을 등록합니다.
     * 이메일은 로그인 식별자로 쓰이므로 저장 전 소문자/공백 정규화를 거치고,
     * 비밀번호는 BCrypt로 단방향 해시해 DB에 원문이 남지 않게 합니다.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest dto) {
        User user = User.builder()
                .email(normalizeEmail(dto.getEmail()))
                .password(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname())
                .build();
        userRepository.save(user);
        return ResponseEntity.ok("요원 등록 완료");
    }

    /**
     * 이메일/비밀번호를 검증하고 프론트가 이후 요청에 사용할 JWT와 사용자 요약 정보를 반환합니다.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest dto) {
        User user = userRepository.findByEmail(normalizeEmail(dto.getEmail()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "미등록 요원"));

        if(!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "비밀번호 불일치");
        }

        String token = jwtTokenProvider.createToken(user.getEmail());

        return ResponseEntity.ok(AuthResponse.of(token, user));
    }

    /**
     * 같은 이메일이 대소문자만 다르게 중복 저장되는 문제를 막기 위한 공통 정규화 함수입니다.
     */
    private String normalizeEmail(String email) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일은 필수입니다.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
