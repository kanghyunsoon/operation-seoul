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

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일은 필수입니다.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
