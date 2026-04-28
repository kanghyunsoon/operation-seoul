package com.operation.seoul.auth.controller;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.auth.security.JwtTokenProvider;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserDto dto) {
        User user = User.builder()
                .email(dto.email)
                .password(passwordEncoder.encode(dto.password)) // 암호화 필수!
                .nickname(dto.nickname)
                .build();
        userRepository.save(user);
        return ResponseEntity.ok("요원 등록 완료");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDto dto) {
        User user = userRepository.findByEmail(dto.email)
                .orElseThrow(() -> new IllegalArgumentException("미등록 요원"));

        if(!passwordEncoder.matches(dto.password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호 불일치");
        }

        String token = jwtTokenProvider.createToken(user.getEmail());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of("nickname", user.getNickname(), "email", user.getEmail())
        ));
    }

    @Data
    static class UserDto {
        private String email;
        private String password;
        private String nickname;
    }
}