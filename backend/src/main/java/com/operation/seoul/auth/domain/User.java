package com.operation.seoul.auth.domain;

import lombok.*;

/**
 * 사용자 계정 도메인 모델입니다.
 * 과제 제약에 맞춰 JPA 엔티티가 아닌 MyBatis 매퍼의 파라미터/결과 객체로 사용합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** 사용자 내부 식별자입니다. 다른 도메인에서는 현재 FK 객체 대신 이 id 값을 저장합니다. */
    private Long id;

    /** 로그인 ID로 사용하는 이메일입니다. AuthController에서 소문자/공백 정규화 후 저장합니다. */
    private String email;

    /** BCrypt로 해시된 비밀번호입니다. 원문 비밀번호를 저장하지 않습니다. */
    private String password;

    /** 화면에서 요원명으로 표시되는 사용자 이름입니다. */
    private String nickname;

    /** 관리자 API 접근과 프론트 관리자 패널 표시를 결정하는 권한 플래그입니다. */
    @Builder.Default
    private boolean admin = false;
}
