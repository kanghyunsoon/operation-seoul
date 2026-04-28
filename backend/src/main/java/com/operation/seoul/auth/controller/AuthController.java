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
@CrossOrigin(origins = "http://localhost:5173") // 임시진행
public class AuthController {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest dto) {
        User user = User.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname())
                .build();
        userRepository.save(user);
        return ResponseEntity.ok("요원 등록 완료");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("미등록 요원"));

        if(!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호 불일치");
        }

        String token = jwtTokenProvider.createToken(user.getEmail());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of("nickname", user.getNickname(), "email", user.getEmail())
        ));
    }

    @Data
    static class AuthRequest {
        private String email;
        private String password;
        private String nickname;
    }
}