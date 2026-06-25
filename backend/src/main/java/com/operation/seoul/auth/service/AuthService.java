package com.operation.seoul.auth.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.dto.AuthRequest;
import com.operation.seoul.auth.dto.AuthResponse;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.auth.security.JwtTokenProvider;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    public void register(AuthRequest request) {
        String email = normalizeEmail(request.getEmail());
        String password = requireText(request.getPassword(), "INVALID_INPUT", "필수 입력값을 확인해 주세요.");
        String nickname = requireText(request.getNickname(), "INVALID_INPUT", "필수 입력값을 확인해 주세요.");
        if (password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호는 최소 8자 이상이어야 합니다.");
        }
        if (userRepository.countByEmail(email) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.");
        }
        if (userRepository.countByNickname(nickname) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
        }

        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .nickname(nickname)
                .role("ROLE_USER")
                .status("ACTIVE")
                .admin(false)
                .build());
    }

    public AuthResponse login(AuthRequest request) {
        String email = normalizeEmail(request.getEmail());
        String password = requireText(request.getPassword(), "INVALID_INPUT", "필수 입력값을 확인해 주세요.");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요."));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
        }
        return createLoginResponse(user);
    }

    public AuthResponse createLoginResponse(User user) {
        if ("SUSPENDED".equals(user.effectiveStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "이용이 제한된 계정입니다.");
        }
        if ("DELETED".equals(user.effectiveStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DELETED", "탈퇴한 계정입니다.");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "비활성화된 계정입니다.");
        }
        return AuthResponse.of(jwtTokenProvider.createToken(user.getEmail()), user);
    }

    private String normalizeEmail(String email) {
        return requireText(email, "INVALID_INPUT", "필수 입력값을 확인해 주세요.")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String code, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value.trim();
    }
}
