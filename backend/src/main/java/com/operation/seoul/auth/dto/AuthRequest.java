package com.operation.seoul.auth.dto;

import lombok.Data;

@Data
public class AuthRequest {
    /** 로그인/회원가입 공통 식별자입니다. */
    private String email;

    /** 로그인 검증 또는 회원가입 저장에 사용하는 원문 비밀번호입니다. */
    private String password;

    /** 회원가입 때만 사용하는 코드네임입니다. 로그인 요청에서는 비어 있어도 됩니다. */
    private String nickname;
}
