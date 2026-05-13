package com.operation.seoul.auth.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    /** 사용자 내부 식별자입니다. 다른 도메인에서는 현재 FK 객체 대신 이 id 값을 저장합니다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 ID로 사용하는 이메일입니다. AuthController에서 소문자/공백 정규화 후 저장합니다. */
    @Column(unique = true, nullable = false)
    private String email;

    /** BCrypt로 해시된 비밀번호입니다. 원문 비밀번호를 저장하지 않습니다. */
    @Column(nullable = false)
    private String password;

    /** 화면에서 요원명으로 표시되는 사용자 이름입니다. */
    @Column(nullable = false)
    private String nickname;

    /** 관리자 API 접근과 프론트 관리자 패널 표시를 결정하는 권한 플래그입니다. */
    @Builder.Default
    @Column(nullable = false)
    private boolean isAdmin = false;
}
