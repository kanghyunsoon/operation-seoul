package com.operation.seoul.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.auth.dto.AuthResponse;
import com.operation.seoul.auth.dto.OAuthConfigResponse;
import com.operation.seoul.auth.repository.SocialAccountRepository;
import com.operation.seoul.auth.repository.UserRepository;
import com.operation.seoul.global.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

@Service
public class OAuthService {
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RestClient restClient = RestClient.create();
    private final GoogleIdTokenVerifier googleVerifier;
    private final String googleClientId;
    private final String kakaoClientId;
    private final String kakaoClientSecret;
    private final String kakaoRedirectUri;

    public OAuthService(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository,
            AuthService authService,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${oauth.google.client-id:}") String googleClientId,
            @Value("${oauth.kakao.client-id:}") String kakaoClientId,
            @Value("${oauth.kakao.client-secret:}") String kakaoClientSecret,
            @Value("${oauth.kakao.redirect-uri:}") String kakaoRedirectUri
    ) throws GeneralSecurityException, IOException {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.googleClientId = googleClientId.trim();
        this.kakaoClientId = kakaoClientId.trim();
        this.kakaoClientSecret = kakaoClientSecret.trim();
        this.kakaoRedirectUri = kakaoRedirectUri.trim();
        this.googleVerifier = new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance()
        ).setAudience(Collections.singletonList(this.googleClientId)).build();
    }

    public OAuthConfigResponse config() {
        return new OAuthConfigResponse(googleClientId, kakaoClientId, kakaoRedirectUri);
    }

    @Transactional
    public AuthResponse loginWithGoogle(String credential) {
        requireConfigured(googleClientId, "Google OAuth");
        GoogleIdToken token;
        try {
            token = googleVerifier.verify(requireText(credential, "Google credential"));
        } catch (GeneralSecurityException | IOException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google 로그인 정보를 확인할 수 없습니다.");
        }
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "유효하지 않은 Google 로그인 정보입니다.");
        }

        GoogleIdToken.Payload payload = token.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_EMAIL_NOT_VERIFIED", "Google 이메일 인증이 필요합니다.");
        }
        User user = findOrCreateUser("GOOGLE", payload.getSubject(), normalizeEmail(payload.getEmail()),
                value(payload.get("name"), "Google 사용자"), value(payload.get("picture"), null));
        return authService.createLoginResponse(user);
    }

    @Transactional
    public AuthResponse loginWithKakao(String code, String redirectUri) {
        requireConfigured(kakaoClientId, "Kakao OAuth");
        String requestedRedirectUri = requireText(redirectUri, "Kakao redirect URI");
        if (!kakaoRedirectUri.equals(requestedRedirectUri)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REDIRECT_URI", "등록된 Kakao redirect URI와 일치하지 않습니다.");
        }

        KakaoToken token = exchangeKakaoToken(requireText(code, "Kakao authorization code"), requestedRedirectUri);
        KakaoUser profile = fetchKakaoUser(token.accessToken());
        if (profile == null || profile.id() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_KAKAO_USER", "Kakao 사용자 정보를 확인할 수 없습니다.");
        }

        String providerUserId = String.valueOf(profile.id());
        KakaoAccount account = profile.account();
        KakaoProfile kakaoProfile = account == null ? null : account.profile();
        User user = findOrCreateUser(
                "KAKAO",
                providerUserId,
                kakaoEmail(account, providerUserId),
                kakaoProfile == null ? "Kakao 사용자" : value(kakaoProfile.nickname(), "Kakao 사용자"),
                kakaoProfile == null ? null : kakaoProfile.profileImageUrl()
        );
        return authService.createLoginResponse(user);
    }

    private KakaoToken exchangeKakaoToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakaoClientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (!kakaoClientSecret.isBlank()) form.add("client_secret", kakaoClientSecret);
        try {
            KakaoToken response = restClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoToken.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "KAKAO_TOKEN_EXCHANGE_FAILED", "Kakao 인증 토큰을 발급받지 못했습니다.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "KAKAO_TOKEN_EXCHANGE_FAILED", "Kakao 로그인 인증에 실패했습니다.");
        }
    }

    private KakaoUser fetchKakaoUser(String accessToken) {
        try {
            return restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(KakaoUser.class);
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "KAKAO_USER_FETCH_FAILED", "Kakao 사용자 정보를 가져오지 못했습니다.");
        }
    }

    private User findOrCreateUser(String provider, String providerUserId, String email, String nickname, String imageUrl) {
        return socialAccountRepository.findUser(provider, providerUserId).orElseGet(() -> {
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> createUser(email, nickname, imageUrl, providerUserId));
            socialAccountRepository.insert(user.getId(), provider, providerUserId);
            if ((user.getProfileImageUrl() == null || user.getProfileImageUrl().isBlank())
                    && imageUrl != null && !imageUrl.isBlank()) {
                user.setProfileImageUrl(imageUrl);
                userRepository.save(user);
            }
            return user;
        });
    }

    private User createUser(String email, String nickname, String imageUrl, String providerUserId) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .nickname(uniqueNickname(nickname, providerUserId))
                .role("ROLE_USER")
                .status("ACTIVE")
                .profileImageUrl(imageUrl)
                .profilePublic(true)
                .admin(false)
                .build());
    }

    private String uniqueNickname(String requested, String providerUserId) {
        String base = requested == null || requested.isBlank() ? "요원" : requested.trim();
        if (base.length() > 40) base = base.substring(0, 40);
        if (userRepository.countByNickname(base) == 0) return base;
        String suffix = providerUserId.substring(Math.max(0, providerUserId.length() - 6));
        String candidate = base + "_" + suffix;
        int sequence = 2;
        while (userRepository.countByNickname(candidate) > 0) {
            candidate = base + "_" + suffix + "_" + sequence++;
        }
        return candidate;
    }

    private String kakaoEmail(KakaoAccount account, String providerUserId) {
        if (account != null && Boolean.TRUE.equals(account.emailValid())
                && Boolean.TRUE.equals(account.emailVerified())
                && account.email() != null && !account.email().isBlank()) {
            return normalizeEmail(account.email());
        }
        return "kakao_" + providerUserId + "@oauth.operation-korea.local";
    }

    private String normalizeEmail(String email) {
        return requireText(email, "OAuth email").toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OAUTH_REQUEST", label + " 값이 필요합니다.");
        }
        return value.trim();
    }

    private void requireConfigured(String value, String provider) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_NOT_CONFIGURED", provider + " 설정이 필요합니다.");
        }
    }

    private String value(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoToken(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUser(Long id, @JsonProperty("kakao_account") KakaoAccount account) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(
            String email,
            @JsonProperty("is_email_valid") Boolean emailValid,
            @JsonProperty("is_email_verified") Boolean emailVerified,
            KakaoProfile profile
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoProfile(String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {
    }
}
