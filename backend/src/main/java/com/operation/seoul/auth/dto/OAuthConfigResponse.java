package com.operation.seoul.auth.dto;

public record OAuthConfigResponse(
        String googleClientId,
        String kakaoClientId,
        String kakaoRedirectUri
) {
}
