package com.operation.seoul.user.service;

import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.dto.AuthResponse;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.user.dto.PasswordChangeRequest;
import com.operation.seoul.user.dto.UserProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthResponse.UserInfo toUserInfo(User user) {
        return AuthResponse.UserInfo.of(user);
    }

    public AuthResponse.UserInfo updateProfile(User currentUser, UserProfileUpdateRequest request) {
        String nickname = request.getNickname() == null ? currentUser.getNickname() : request.getNickname().trim();
        if (nickname.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "필수 입력값을 확인해 주세요.");
        }
        User duplicated = userRepository.findAll().stream()
                .filter(user -> nickname.equals(user.getNickname()) && !user.getId().equals(currentUser.getId()))
                .findFirst()
                .orElse(null);
        if (duplicated != null) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
        }
        currentUser.setNickname(nickname);
        currentUser.setProfileImageUrl(request.getProfileImageUrl());
        userRepository.save(currentUser);
        return AuthResponse.UserInfo.of(currentUser);
    }

    public void changePassword(User currentUser, PasswordChangeRequest request) {
        if (request.getCurrentPassword() == null || request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호는 최소 길이와 형식 조건을 만족해야 합니다.");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
        }
        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);
    }

    public void softDelete(User currentUser) {
        userRepository.softDeleteById(currentUser.getId());
    }
}