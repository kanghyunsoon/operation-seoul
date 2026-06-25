package com.operation.seoul.auth.dto;

import lombok.Data;

@Data
public class KakaoLoginRequest {
    private String code;
    private String redirectUri;
}
