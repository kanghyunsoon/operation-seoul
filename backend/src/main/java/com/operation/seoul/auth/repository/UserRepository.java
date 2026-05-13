package com.operation.seoul.auth.repository;

import com.operation.seoul.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 사용자 계정 영속성 계층입니다.
 * Spring Data JPA가 기본 CRUD 구현체를 생성하며, 인증 흐름에서는 이메일 조회만 추가로 사용합니다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /** 로그인 및 JWT 인증 필터에서 이메일로 사용자를 찾습니다. */
    Optional<User> findByEmail(String email);
}
